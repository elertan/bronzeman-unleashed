package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.MinigameService;
import com.elertan.PolicyService;
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
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;

@Slf4j
@Singleton
public class PlayerVersusPlayerPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject private Client client;
    @Inject private BUChatService buChatService;
    @Inject private GroundItemOwnedByDataProvider groundItemOwnedByDataProvider;
    @Inject private MinigameService minigameService;
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<PlayerDeathLocation>> deathsByPlayer;

    @Inject
    public PlayerVersusPlayerPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception { deathsByPlayer = new ConcurrentHashMap<>(); }

    @Override
    public void shutDown() throws Exception { deathsByPlayer = null; }

    public void onActorDeath(ActorDeath e) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        if (!(e.getActor() instanceof Player)) return;
        Player otherPlayer = (Player) e.getActor();
        if (Objects.equals(otherPlayer, client.getLocalPlayer())) return;
        if (deathsByPlayer == null) return;
        PolicyContext ctx = createContext();
        if (!ctx.shouldApplyForRules(GameRules::isRestrictPlayerVersusPlayerLoot)) return;
        addPlayerDeathLocation(otherPlayer);
    }

    public void onPlayerLootReceived(PlayerLootReceived e) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        Player player = e.getPlayer();
        if (player == null) return;
        PolicyContext ctx = createContext();
        if (!ctx.shouldApplyForRules(GameRules::isRestrictPlayerVersusPlayerLoot)) return;
        enforcePlayerLootReceivedPolicy(player, e.getItems());
    }

    private void enforcePlayerLootReceivedPolicy(Player player, Collection<ItemStack> itemStacks) {
        if (minigameService.isPlayingLastManStanding()) return;
        String playerName = TextUtils.sanitizePlayerName(player.getName());
        log.info("loot received for player: {}", playerName);
        if (deathsByPlayer == null) { log.info("deathsByPlayer is null"); return; }
        ConcurrentLinkedQueue<PlayerDeathLocation> deaths = deathsByPlayer.get(playerName);
        if (deaths == null || deaths.isEmpty()) { log.info("no death locations for: {}", playerName); return; }
        PlayerDeathLocation loc = deaths.remove();
        if (loc == null || itemStacks == null) return;
        for (ItemStack stack : itemStacks) {
            if (stack == null) continue;
            GroundItemOwnedByKey key = GroundItemOwnedByKey.of(
                stack.getId(), loc.getWorld(), loc.getWorldView().getId(), loc.getWorldPoint());
            markAsPvpLoot(key, playerName).whenComplete((__, t) -> {
                if (t != null) log.error("Failed to mark ground item as PvP loot", t);
            });
        }
    }

    private void addPlayerDeathLocation(Player player) {
        String name = TextUtils.sanitizePlayerName(player.getName());
        WorldPoint wp = player.getWorldLocation();
        WorldView wv = client.findWorldViewFromWorldPoint(wp);
        ConcurrentLinkedQueue<PlayerDeathLocation> q =
            deathsByPlayer.computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>());
        q.add(new PlayerDeathLocation(client.getWorld(), wp, wv, client.getTickCount()));
        log.info("Added death location for {} at tick {} for x:{}, y:{}",
            name, client.getTickCount(), wp.getX(), wp.getY());
    }

    private CompletableFuture<Void> markAsPvpLoot(
        @NonNull GroundItemOwnedByKey key, @NonNull String playerName) {
        ISOOffsetDateTime despawnsAt = new ISOOffsetDateTime(
            OffsetDateTime.now().plus(Duration.ofMinutes(3)));
        GroundItemOwnedByData data = new GroundItemOwnedByData(
            client.getAccountHash(), despawnsAt, playerName);
        return groundItemOwnedByDataProvider.addEntry(key, data).thenApply(__ -> null);
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        PolicyContext ctx = createContext();
        if (!ctx.shouldApplyForRules(GameRules::isRestrictPlayerVersusPlayerLoot)) return;
        Widget widget = event.getWidget();
        if (widget == null) return;
        String menuOption = event.getMenuOption();
        int widgetId = widget.getId();
        if (widgetId == InterfaceID.WildyLootChest.ITEMS
            && (menuOption.startsWith("Take") || menuOption.startsWith("Bank"))) {
            event.consume();
            buChatService.sendRestrictionMessage(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_KEY_RESTRICTION);
        } else if (widgetId == InterfaceID.WildyLootChest.WITHDRAWBANK
            || widgetId == InterfaceID.WildyLootChest.WITHDRAWINV) {
            event.consume();
            buChatService.sendRestrictionMessage(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_KEY_RESTRICTION);
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
