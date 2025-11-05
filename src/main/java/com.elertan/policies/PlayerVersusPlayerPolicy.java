package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUSoundHelper;
import com.elertan.GameRulesService;
import com.elertan.MinigameService;
import com.elertan.PolicyService;
import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.data.GroundItemOwnedByDataProvider;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.utils.TextUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;

@Slf4j
@Singleton
public class PlayerVersusPlayerPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private GroundItemOwnedByDataProvider groundItemOwnedByDataProvider;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private MinigameService minigameService;

    private ConcurrentHashMap<String, ConcurrentLinkedQueue<PlayerDeathLocation>> playerDeathLocationsByPlayerName;

    @Inject
    public PlayerVersusPlayerPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {
        playerDeathLocationsByPlayerName = new ConcurrentHashMap<>();
    }

    @Override
    public void shutDown() throws Exception {
        playerDeathLocationsByPlayerName = null;
    }

    public void onActorDeath(ActorDeath e) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        Actor actor = e.getActor();
        boolean isPlayer = actor instanceof Player;
        if (!isPlayer) {
            log.debug("Actor is not a player");
            return;
        }
        Player otherPlayer = (Player) actor;
        Player localPlayer = client.getLocalPlayer();
        if (Objects.equals(actor, localPlayer)) {
            log.debug("we died...");
            return;
        }
        if (playerDeathLocationsByPlayerName == null) {
            log.debug("playerDeathLocations is null");
            return;
        }
        PolicyContext policyContext = createContext();
        GameRules gameRules = policyContext.getGameRules();
        if (policyContext.isMustEnforceStrictPolicies()) {
            addPlayerDeathLocation(otherPlayer);
            return;
        }
        if (gameRules == null || !gameRules.isRestrictPlayerVersusPlayerLoot()) {
            return;
        }
        addPlayerDeathLocation(otherPlayer);
    }

    public void onPlayerLootReceived(PlayerLootReceived e) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        // This covers PvP loot, including loot keys when opened.
        // Tag this batch as PvP using e.getPlayerName() or source info.
        Player player = e.getPlayer();
        if (player == null) {
            return;
        }
        Collection<ItemStack> itemStacks = e.getItems();

        PolicyContext policyContext = createContext();
        GameRules gameRules = policyContext.getGameRules();
        if (policyContext.isMustEnforceStrictPolicies()) {
            enforcePlayerLootReceivedPolicy(player, itemStacks);
            return;
        }
        if (gameRules == null || !gameRules.isRestrictPlayerVersusPlayerLoot()) {
            return;
        }
        enforcePlayerLootReceivedPolicy(player, itemStacks);
    }

    private void enforcePlayerLootReceivedPolicy(Player player, Collection<ItemStack> itemStacks) {
        // Disable for last man standing
        if (minigameService.isPlayingLastManStanding()) {
            return;
        }

        String playerName = TextUtils.sanitizePlayerName(player.getName());
        log.info("loot received for player: {}", playerName);

        if (playerDeathLocationsByPlayerName == null) {
            log.info("playerDeathLocations is null");
            return;
        }
        ConcurrentLinkedQueue<PlayerDeathLocation> playerDeathLocations = playerDeathLocationsByPlayerName.get(
            playerName);
        if (playerDeathLocations == null) {
            log.info("playerDeathLocations is null for player: {}", playerName);
            return;
        }
        if (playerDeathLocations.isEmpty()) {
            log.info("playerDeathLocations is empty for player: {}", playerName);
            return;
        }
        PlayerDeathLocation lastDeathLocation = playerDeathLocations.remove();
        if (lastDeathLocation == null) {
            log.info("lastDeathLocation is null for player: {}", playerName);
            return;
        }

        if (itemStacks == null) {
            log.info("item stacks is null, ignoring");
            return;
        }
        for (ItemStack itemStack : itemStacks) {
            if (itemStack == null) {
                continue;
            }

            int itemId = itemStack.getId();
            int world = lastDeathLocation.getWorld();
            WorldPoint worldPoint = lastDeathLocation.getWorldPoint();
            WorldView worldView = lastDeathLocation.getWorldView();
            int plane = worldPoint.getPlane();

            GroundItemOwnedByKey key = GroundItemOwnedByKey.builder()
                .itemId(itemId)
                .world(world)
                .worldViewId(worldView.getId())
                .plane(plane)
                .worldX(worldPoint.getX())
                .worldY(worldPoint.getY())
                .build();

            markGroundItemOwnedByAsPlayerVersusPlayerLoot(key, playerName)
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.error(
                            "Failed to mark ground item as player versus player loot",
                            throwable
                        );
                    }
                });
        }
    }

    private void addPlayerDeathLocation(Player player) {
        long tickCount = client.getTickCount();

        String playerName = TextUtils.sanitizePlayerName(player.getName());
        WorldPoint worldPoint = player.getWorldLocation();

        int world = client.getWorld();
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);

        ConcurrentLinkedQueue<PlayerDeathLocation> deathLocations = playerDeathLocationsByPlayerName.computeIfAbsent(
            playerName,
            k -> new ConcurrentLinkedQueue<>()
        );
        PlayerDeathLocation playerDeathLocation = new PlayerDeathLocation(
            world,
            worldPoint,
            worldView,
            tickCount
        );
        deathLocations.add(playerDeathLocation);
        log.info(
            "Added death location for player {} at tick count {} for x: {}, y: {}",
            playerName,
            tickCount,
            worldPoint.getX(),
            worldPoint.getY()
        );
    }

    private CompletableFuture<Void> markGroundItemOwnedByAsPlayerVersusPlayerLoot(
        @NonNull GroundItemOwnedByKey key, @NonNull String playerName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        GroundItemOwnedByData data;
        if (map == null) {
            data = null;
        } else {
            data = map.get(key);
        }

        if (data == null) {
            // We can assume player vs player loot despawns after 3 minutes (300 ticks)
            ISOOffsetDateTime despawnsAt = new ISOOffsetDateTime(OffsetDateTime.now()
                .plus(Duration.ofMinutes(3)));
            data = new GroundItemOwnedByData(client.getAccountHash(), despawnsAt, playerName);
        } else {
            long accountHash = data.getAccountHash();
            ISOOffsetDateTime despawnsAt = data.getDespawnsAt();
            data = new GroundItemOwnedByData(accountHash, despawnsAt, playerName);
        }

        groundItemOwnedByDataProvider.update(key, data)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider update failed", throwable);
                    future.completeExceptionally(throwable);
                    return;
                }

                future.complete(null);
            });

        return future;
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        String menuOption = event.getMenuOption();
        Widget widget = event.getWidget();
        if (widget == null) {
            return;
        }
        int widgetId = widget.getId();
        if (widgetId == InterfaceID.WildyLootChest.ITEMS) {
            if (menuOption.startsWith("Take") || menuOption.startsWith("Bank")) {
                event.consume();

                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append(
                    buPluginConfig.chatRestrictionColor(),
                    chatMessageProvider.messageFor(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_KEY_RESTRICTION)
                );
                buChatService.sendMessage(builder.build());
                buSoundHelper.playDisabledSound();
                return;
            }
        }
        if (widgetId == InterfaceID.WildyLootChest.WITHDRAWBANK
            || widgetId == InterfaceID.WildyLootChest.WITHDRAWINV) {
            event.consume();

            ChatMessageBuilder builder = new ChatMessageBuilder();
            builder.append(
                buPluginConfig.chatRestrictionColor(),
                chatMessageProvider.messageFor(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_KEY_RESTRICTION)
            );
            buChatService.sendMessage(builder.build());
            buSoundHelper.playDisabledSound();
        }
    }

    @Value
    private static class PlayerDeathLocation {

        int world;
        WorldPoint worldPoint;
        WorldView worldView;
        long tickCount;
    }
}
