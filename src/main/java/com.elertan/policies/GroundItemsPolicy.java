package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUSoundHelper;
import com.elertan.GameRulesService;
import com.elertan.MemberService;
import com.elertan.MinigameService;
import com.elertan.PolicyService;
import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.data.GroundItemOwnedByDataProvider;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.elertan.utils.TickUtils;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.chat.ChatMessageBuilder;

@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private GroundItemOwnedByDataProvider groundItemOwnedByDataProvider;
    @Inject
    private MemberService memberService;
    @Inject
    private MinigameService minigameService;

    private GroundItemOwnedByDataProvider.Listener groundItemOwnedByDataProviderListener;
    private ScheduledExecutorService scheduler;

    @Inject
    public GroundItemsPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {
        groundItemOwnedByDataProviderListener = new GroundItemOwnedByDataProvider.Listener() {
            @Override
            public void onReadAll(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map) {

            }

            @Override
            public void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value) {

            }

            @Override
            public void onDelete(GroundItemOwnedByKey key) {

            }
        };
        groundItemOwnedByDataProvider.addMapListener(groundItemOwnedByDataProviderListener);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupExpiredGroundItems, 10, 10, TimeUnit.SECONDS);

        groundItemOwnedByDataProvider.waitUntilReady(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider waitUntilReady failed", throwable);
                return;
            }

            cleanupExpiredGroundItemsForEveryone();
        });
    }

    @Override
    public void shutDown() throws Exception {
        groundItemOwnedByDataProvider.removeMapListener(groundItemOwnedByDataProviderListener);

        scheduler.shutdownNow();
    }

    public void onItemSpawned(ItemSpawned event) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        PolicyContext context = createContext();
        GameRules gameRules = context.getGameRules();
        if (gameRules == null || !gameRules.isRestrictGroundItems()) {
            return;
        }

        TileItem tileItem = event.getItem();
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF
            && tileItem.getOwnership() != TileItem.OWNERSHIP_GROUP) {
            // item does not belong to me, ignore it
            return;
        }
        Tile tile = event.getTile();

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);

        int itemId = tileItem.getId();
        int world = client.getWorld();
        int plane = worldPoint.getPlane();

        GroundItemOwnedByKey key = GroundItemOwnedByKey.builder()
            .itemId(itemId)
            .world(world)
            .worldViewId(worldView.getId())
            .plane(plane)
            .worldX(worldPoint.getX())
            .worldY(worldPoint.getY())
            .build();

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            log.warn("Ground item spawned for me but groundItemOwnedByMap is null");
            return;
        }

        GroundItemOwnedByData groundItemOwnedByData = groundItemOwnedByMap.get(key);
        if (groundItemOwnedByData != null
            && groundItemOwnedByData.getAccountHash() == client.getAccountHash()) {
            log.debug("gi {} already in groundItemOwnedByMap for me, ignore", key);
            return;
        }
        long despawnTimeTicks = tileItem.getDespawnTime() - client.getTickCount();
        Duration despawnDuration = TickUtils.ticksToDuration(despawnTimeTicks);
        OffsetDateTime despawnsAt = OffsetDateTime.now().plus(despawnDuration);
        GroundItemOwnedByData newGroundItemOwnedByData = new GroundItemOwnedByData(
            client.getAccountHash(),
            new ISOOffsetDateTime(despawnsAt),
            null
        );

        groundItemOwnedByDataProvider.update(key, newGroundItemOwnedByData)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider update failed", throwable);
                }
            });
    }

    public void onItemDespawned(ItemDespawned event) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        TileItem tileItem = event.getItem();
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF
            && tileItem.getOwnership() != TileItem.OWNERSHIP_GROUP) {
            // item does not belong to me, ignore it
            return;
        }
        Tile tile = event.getTile();

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);

        int itemId = tileItem.getId();
        int world = client.getWorld();
        int plane = worldPoint.getPlane();

        GroundItemOwnedByKey key = GroundItemOwnedByKey.builder()
            .itemId(itemId)
            .world(world)
            .worldViewId(worldView.getId())
            .plane(plane)
            .worldX(worldPoint.getX())
            .worldY(worldPoint.getY())
            .build();

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            return;
        }

        GroundItemOwnedByData groundItemOwnedByData = groundItemOwnedByMap.get(key);
        if (groundItemOwnedByData == null) {
            log.debug("gi {} already deleted, ignore", key);
            return;
        }

        groundItemOwnedByDataProvider.delete(key).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider delete failed", throwable);
            }
        });
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        PolicyContext context = createContext();
        enforceItemTakePolicyWhereNecessary(event, context);
    }

    private void enforceItemTakePolicyWhereNecessary(MenuOptionClicked event,
        PolicyContext context) {
        MenuAction menuAction = event.getMenuAction();
        String menuOption = event.getMenuOption();
        boolean isGroundItemMenuAction =
            menuAction.ordinal() >= MenuAction.GROUND_ITEM_FIRST_OPTION.ordinal()
                && menuAction.ordinal() <= MenuAction.GROUND_ITEM_FIFTH_OPTION.ordinal();
        boolean isWidgetTargetOnGroundItemAction =
            menuAction.ordinal() == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM.ordinal();
        if (!isGroundItemMenuAction && !isWidgetTargetOnGroundItemAction) {
            return;
        }

        if (!menuOption.equals("Take") && !menuOption.equals("Cast")) {
            return;
        }
        int itemId = event.getId();
        if (itemId <= 1) {
            return;
        }

        // In last man standing we want to allow taking any items
        if (minigameService.isPlayingLastManStanding()) {
            return;
        }

        GetClickedTileItemOutput output = getClickedTileItem(event);
        if (output == null) {
            log.warn(
                "Ground item not found at scene ({}, {}) for id {}",
                event.getParam0(),
                event.getParam1(),
                itemId
            );
            return;
        }
        Tile tile = output.getTile();
        TileItem tileItem = output.getTileItem();

        ItemComposition itemComposition = client.getItemDefinition(itemId);

        int ownership = tileItem.getOwnership();
        if (ownership == TileItem.OWNERSHIP_NONE) {
            log.debug("Item '{}' is not owned by anyone, allow take", itemComposition.getName());
            return;
        }

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);

        int world = client.getWorld();
        int plane = worldPoint.getPlane();

        GroundItemOwnedByKey key = GroundItemOwnedByKey.builder()
            .itemId(itemId)
            .world(world)
            .worldViewId(worldView.getId())
            .plane(plane)
            .worldX(worldPoint.getX())
            .worldY(worldPoint.getY())
            .build();

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            boolean mustPerformCheck =
                context.isMustEnforceStrictPolicies() || (context.getGameRules() != null && (
                    context.getGameRules().isRestrictGroundItems() || context.getGameRules()
                        .isRestrictPlayerVersusPlayerLoot()
                ));
            if (mustPerformCheck) {
                event.consume();
                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append(
                    buPluginConfig.chatErrorColor(),
                    chatMessageProvider.messageFor(MessageKey.STILL_LOADING_PLEASE_WAIT)
                );
                buChatService.sendMessage(builder.build());
            }
            return;
        }
        GroundItemOwnedByData groundItemOwnedByData = groundItemOwnedByMap.get(key);
        if (groundItemOwnedByData != null) {
            // Our group owns the item

            // But could still be a pvp acquired item
            boolean mustPerformPlayerVersusPlayerCheck =
                context.isMustEnforceStrictPolicies() || (context.getGameRules() != null
                    && context.getGameRules().isRestrictPlayerVersusPlayerLoot());

            String droppedByPlayerName = groundItemOwnedByData.getDroppedByPlayerName();
            if (mustPerformPlayerVersusPlayerCheck && droppedByPlayerName != null) {
                log.info(
                    "Performing player versus player loot check for item '{}' dropped by {}",
                    itemId,
                    droppedByPlayerName
                );

                Member member = null;
                try {
                    member = memberService.getMemberByName(droppedByPlayerName);
                } catch (Exception ignored) {
                }
                if (member != null) {
                    log.info("Player '{}' is part of our group, allow take", droppedByPlayerName);
                    return;
                }

                log.info("Player '{}' is not part of our group, deny take", droppedByPlayerName);
                event.consume();
                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append(
                    buPluginConfig.chatRestrictionColor(),
                    chatMessageProvider.messageFor(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_RESTRICTION)
                );
                buChatService.sendMessage(builder.build());
            }
            return;
        }

        if (ownership == TileItem.OWNERSHIP_SELF) {
            log.debug("Item '{}' is owned by me, allow take", itemComposition.getName());
            return;
        }

        boolean mustPerformGroundItemsCheck =
            context.isMustEnforceStrictPolicies() || (context.getGameRules() != null
                && context.getGameRules().isRestrictGroundItems());
        if (mustPerformGroundItemsCheck) {
            event.consume();
            ChatMessageBuilder builder = new ChatMessageBuilder();
            if (isWidgetTargetOnGroundItemAction) {
                builder.append(
                    buPluginConfig.chatRestrictionColor(),
                    chatMessageProvider.messageFor(MessageKey.GROUND_ITEM_CAST_RESTRICTION)
                );
            } else {
                builder.append(
                    buPluginConfig.chatRestrictionColor(),
                    chatMessageProvider.messageFor(MessageKey.GROUND_ITEM_TAKE_RESTRICTION)
                );
            }
            buChatService.sendMessage(builder.build());
        }
    }

    private GetClickedTileItemOutput getClickedTileItem(MenuOptionClicked event) {
        // For ground item menu actions, param0 = scene X, param1 = scene Y, id = item ID
        final int sceneX = event.getParam0();
        final int sceneY = event.getParam1();
        final int itemId = event.getId();
        final int plane = client.getPlane();

        Scene scene = client.getScene();
        if (scene == null) {
            return null;
        }

        Tile[][][] tiles = scene.getTiles();
        if (tiles == null || plane < 0 || plane >= tiles.length) {
            return null;
        }

        if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length
            || sceneY >= tiles[plane][sceneX].length) {
            return null;
        }

        Tile tile = tiles[plane][sceneX][sceneY];
        if (tile == null || tile.getGroundItems() == null) {
            return null;
        }

        for (TileItem ti : tile.getGroundItems()) {
            if (ti.getId() == itemId) {
                return new GetClickedTileItemOutput(tile, ti);
            }
        }
        return null;
    }

//    private void takeItemOwnedByGroupMember(GroundItemOwnedByKey key) {
//        groundItemOwnedByDataProvider.delete(key).whenComplete((__, throwable) -> {
//            if (throwable != null) {
//                log.error("Failed to delte ground item {} from group member", key, throwable);
//                return;
//            }
//        });
//    }

    private void cleanupExpiredGroundItems() {
        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (map == null || map.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        long accountHash = client.getAccountHash();

        for (Map.Entry<GroundItemOwnedByKey, GroundItemOwnedByData> entry : map.entrySet()) {
            GroundItemOwnedByKey key = entry.getKey();
            GroundItemOwnedByData data = entry.getValue();
            if (data.getAccountHash() != accountHash) {
                continue;
            }
            if (data.getDespawnsAt().getValue().isAfter(now)) {
                continue;
            }

            log.debug("Cleaning up expired ground item {}", key);

            groundItemOwnedByDataProvider.delete(key).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to clean up expired ground item {}", key, throwable);
                }
            });
        }
    }

    private void cleanupExpiredGroundItemsForEveryone() {
        log.debug("Cleaning up expired ground items for everyone");

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (map == null || map.isEmpty()) {
            log.debug("Ground item owned by map is empty, nothing to clean up");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        for (Map.Entry<GroundItemOwnedByKey, GroundItemOwnedByData> entry : map.entrySet()) {
            GroundItemOwnedByKey key = entry.getKey();
            GroundItemOwnedByData data = entry.getValue();
            if (data.getDespawnsAt().getValue().isAfter(now)) {
                continue;
            }

            log.debug(
                "Cleaning up expired ground item {} for account hash: {}",
                key,
                data.getAccountHash()
            );

            groundItemOwnedByDataProvider.delete(key).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to clean up expired ground item {}", key, throwable);
                }
            });
        }
    }

    @AllArgsConstructor
    private static class GetClickedTileItemOutput {

        @Getter
        @NonNull
        private Tile tile;
        @Getter
        @NonNull
        private TileItem tileItem;
    }
}
