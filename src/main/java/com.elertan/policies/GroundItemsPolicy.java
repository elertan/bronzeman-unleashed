package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.MemberService;
import com.elertan.MinigameService;
import com.elertan.PolicyService;
import com.elertan.WorldTypeService;
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
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;

/**
 * Ground-item Bronzeman rules and Firebase-backed ownership for group drops.
 * <p>
 * Every group member's client receives the same {@link net.runelite.api.events.ItemDespawned} /
 * {@link ItemQuantityChanged} events. Decrements are written through {@link GroundItemOwnedByDataProvider}
 * to shared storage, so only the client that actually performed an allowed ground interaction (within a few ticks)
 * may emit those writes; see {@link #shouldConsumeSharedOwnership}.
 */
@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {

    private static final int PENDING_LOOT_TICK_WINDOW = 3;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
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

    /**
     * Expiry tick (inclusive) for a pending allowed Take; only this client may decrement Firebase for that key.
     */
    private final ConcurrentHashMap<GroundItemOwnedByKey, Integer> pendingLootAttemptExpiresAtTick
        = new ConcurrentHashMap<>();

    @Inject
    public GroundItemsPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService) {
        super(accountConfigurationService, gameRulesService, policyService, worldTypeService);
    }

    @Override
    public void startUp() throws Exception {
        groundItemOwnedByDataProviderListener = new GroundItemOwnedByDataProvider.Listener() {
            @Override
            public void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map) {
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

        groundItemOwnedByDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider await failed", throwable);
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

    public void onGameTick(GameTick event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            pendingLootAttemptExpiresAtTick.clear();
            return;
        }
        cleanupExpiredLootAttempts(client.getTickCount());
    }

    public void onItemSpawned(ItemSpawned event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isRestrictGroundItems)) {
            return;
        }

        TileItem tileItem = event.getItem();
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF
            && tileItem.getOwnership() != TileItem.OWNERSHIP_GROUP) {
            return;
        }
        Tile tile = event.getTile();
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap
            = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            log.warn("Ground item spawned for me but groundItemOwnedByMap is null");
            return;
        }

        long despawnTimeTicks = tileItem.getDespawnTime() - client.getTickCount();
        Duration despawnDuration = TickUtils.ticksToDuration(despawnTimeTicks);
        OffsetDateTime despawnsAt = OffsetDateTime.now().plus(despawnDuration);
        int quantity = Math.max(1, tileItem.getQuantity());
        GroundItemOwnedByData newGroundItemOwnedByData = new GroundItemOwnedByData(
            client.getAccountHash(),
            new ISOOffsetDateTime(despawnsAt),
            quantity,
            null
        );

        groundItemOwnedByDataProvider.updatePile(key, newGroundItemOwnedByData)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider updatePile failed", throwable);
                }
            });
    }

    public void onItemDespawned(ItemDespawned event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        TileItem tileItem = event.getItem();
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF
            && tileItem.getOwnership() != TileItem.OWNERSHIP_GROUP) {
            return;
        }
        Tile tile = event.getTile();
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);

        int trackedQty = groundItemOwnedByDataProvider.getTotalOwnedQuantity(key);
        if (trackedQty <= 0) {
            log.debug("gi {} has no tracked quantity, ignore despawn", key);
            return;
        }

        if (!shouldConsumeSharedOwnership(
            trackedQty,
            pendingLootAttemptExpiresAtTick.get(key),
            client.getTickCount()
        )) {
            return;
        }

        int removeQty = Math.max(1, tileItem.getQuantity());
        groundItemOwnedByDataProvider.consumeQuantity(key, removeQty).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider consumeQuantity failed", throwable);
            }
        });
    }

    public void onItemQuantityChanged(ItemQuantityChanged event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        int oldQuantity = event.getOldQuantity();
        int newQuantity = event.getNewQuantity();
        int delta = newQuantity - oldQuantity;
        if (delta == 0) {
            return;
        }

        TileItem tileItem = event.getItem();
        Tile tile = event.getTile();
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);

        boolean selfOrGroup = tileItem.getOwnership() == TileItem.OWNERSHIP_SELF
            || tileItem.getOwnership() == TileItem.OWNERSHIP_GROUP;
        int trackedQty = groundItemOwnedByDataProvider.getTotalOwnedQuantity(key);
        boolean hasTracked = trackedQty > 0;

        if (delta > 0) {
            if (!selfOrGroup) {
                return;
            }
            long despawnTimeTicks = tileItem.getDespawnTime() - client.getTickCount();
            Duration despawnDuration = TickUtils.ticksToDuration(despawnTimeTicks);
            OffsetDateTime despawnsAt = OffsetDateTime.now().plus(despawnDuration);
            GroundItemOwnedByData existing = groundItemOwnedByDataProvider.getPile(key);
            int baseQty = existing == null ? 0 : existing.getQuantityOrDefaultOne();
            int mergedQty = baseQty + delta;
            GroundItemOwnedByData data = new GroundItemOwnedByData(
                client.getAccountHash(),
                new ISOOffsetDateTime(despawnsAt),
                mergedQty,
                existing != null ? existing.getDroppedByPlayerName() : null
            );
            groundItemOwnedByDataProvider.updatePile(key, data).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider updatePile failed", throwable);
                }
            });
            return;
        }

        if (!selfOrGroup && !hasTracked) {
            return;
        }

        if (!shouldConsumeSharedOwnership(
            trackedQty,
            pendingLootAttemptExpiresAtTick.get(key),
            client.getTickCount()
        )) {
            return;
        }

        int consumeQty = Math.abs(delta);
        groundItemOwnedByDataProvider.consumeQuantity(key, consumeQty).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider consumeQuantity failed", throwable);
            }
        });
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        PolicyContext context = createContext();
        enforceItemTakePolicyWhereNecessary(event, context);
    }

    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }
        // Skip ground-item menu reordering in LMS (click enforcement already bypasses there).
        if (minigameService.isPlayingLastManStanding()) {
            return;
        }

        PolicyContext context = createContext();
        if (!shouldDeprioritizeUnlootableMenuEntries(context)) {
            return;
        }

        MenuEntry menuEntry = event.getMenuEntry();
        if (menuEntry == null) {
            return;
        }
        MenuAction type = menuEntry.getType();
        if (!isGroundItemMenuAction(type)) {
            return;
        }

        if (isGroundItemExamineMenuOption(menuEntry.getOption())) {
            return;
        }

        int itemId = menuEntry.getIdentifier();
        if (itemId <= 1) {
            return;
        }

        GetClickedTileItemOutput output = getClickedTileItemFromScene(
            menuEntry.getParam0(),
            menuEntry.getParam1(),
            itemId
        );
        if (output == null) {
            return;
        }

        boolean widgetTarget = type == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
        EligibilityDecision decision = evaluateTakeEligibility(
            context,
            itemId,
            output.getTile(),
            output.getTileItem(),
            widgetTarget
        );
        if (decision.getAction() == EligibilityAction.DENY) {
            menuEntry.setDeprioritized(true);
        }
    }

    private boolean shouldDeprioritizeUnlootableMenuEntries(PolicyContext context) {
        if (context.isMustEnforceStrictPolicies()) {
            return true;
        }
        GameRules rules = context.getGameRules();
        return rules != null && rules.isRestrictGroundItems();
    }

    private static boolean isGroundItemMenuAction(MenuAction menuAction) {
        return menuAction.ordinal() >= MenuAction.GROUND_ITEM_FIRST_OPTION.ordinal()
            && menuAction.ordinal() <= MenuAction.GROUND_ITEM_FIFTH_OPTION.ordinal()
            || menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
    }

    /**
     * Non-interactive option we never deprioritize or block. All other {@link #isGroundItemMenuAction} entries
     * (Take, Light, Cast-on-item, etc.) use Jagex wording; we key off {@link MenuAction}, not the label.
     * <p>
     * "Walk here" is {@link MenuAction#WALK} on the tile, not a ground-item action, so it stays above deprioritized
     * ground options.
     */
    private static boolean isGroundItemExamineMenuOption(String option) {
        return option != null && "Examine".equals(option);
    }

    private void enforceItemTakePolicyWhereNecessary(MenuOptionClicked event, PolicyContext context) {
        MenuAction menuAction = event.getMenuAction();
        if (!isGroundItemMenuAction(menuAction)) {
            return;
        }

        if (isGroundItemExamineMenuOption(event.getMenuOption())) {
            return;
        }
        int itemId = event.getId();
        if (itemId <= 1) {
            return;
        }

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

        boolean widgetTarget = menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
        EligibilityDecision decision = evaluateTakeEligibility(
            context,
            itemId,
            output.getTile(),
            output.getTileItem(),
            widgetTarget
        );

        if (decision.getAction() == EligibilityAction.ALLOW) {
            recordPendingLootAttempt(itemId, output.getTile());
            return;
        }

        if (decision.getAction() == EligibilityAction.LOADING) {
            event.consume();
            buChatService.sendErrorMessage(chatMessageProvider.messageFor(MessageKey.STILL_LOADING_PLEASE_WAIT));
            return;
        }

        event.consume();
        MessageKey messageKey = decision.getDenyMessageKey();
        if (messageKey == MessageKey.GROUND_ITEM_TAKE_RESTRICTION
            || messageKey == MessageKey.GROUND_ITEM_CAST_RESTRICTION) {
            messageKey = widgetTarget
                ? MessageKey.GROUND_ITEM_CAST_RESTRICTION
                : MessageKey.GROUND_ITEM_TAKE_RESTRICTION;
        }
        buChatService.sendRestrictionMessage(messageKey);
    }

    private EligibilityDecision evaluateTakeEligibility(PolicyContext context, int itemId, Tile tile,
        TileItem tileItem, boolean widgetTargetOnGroundItem) {
        ItemComposition itemComposition = client.getItemDefinition(itemId);
        int ownership = tileItem.getOwnership();

        if (ownership == TileItem.OWNERSHIP_NONE) {
            log.debug("Item '{}' is not owned by anyone, allow take", itemComposition.getName());
            return EligibilityDecision.allow();
        }

        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);
        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap
            = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            boolean mustPerformCheck =
                context.isMustEnforceStrictPolicies() || (context.getGameRules() != null && (
                    context.getGameRules().isRestrictGroundItems()
                        || context.getGameRules().isRestrictPlayerVersusPlayerLoot()
                ));
            return mustPerformCheck
                ? EligibilityDecision.loading()
                : EligibilityDecision.allow();
        }

        if (groundItemOwnedByDataProvider.hasEntries(key)) {
            boolean mustPerformPlayerVersusPlayerCheck =
                context.isMustEnforceStrictPolicies() || (context.getGameRules() != null
                    && context.getGameRules().isRestrictPlayerVersusPlayerLoot());

            if (mustPerformPlayerVersusPlayerCheck) {
                GroundItemOwnedByData pile = groundItemOwnedByDataProvider.getPile(key);
                if (pile != null) {
                    String droppedByPlayerName = pile.getDroppedByPlayerName();
                    if (droppedByPlayerName != null) {
                        log.debug(
                            "Performing player versus player loot check for item '{}' dropped by {}",
                            itemId,
                            droppedByPlayerName
                        );

                        Member member = null;
                        try {
                            member = memberService.getMemberByName(droppedByPlayerName);
                        } catch (Exception ignored) {
                        }
                        if (member == null) {
                            log.debug("Player '{}' is not part of our group, deny take", droppedByPlayerName);
                            return EligibilityDecision.deny(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_RESTRICTION);
                        }
                        log.debug("Player '{}' is part of our group, allow take", droppedByPlayerName);
                    }
                }
            }
            return EligibilityDecision.allow();
        }

        if (ownership == TileItem.OWNERSHIP_SELF) {
            log.debug("Item '{}' is owned by me, allow take", itemComposition.getName());
            return EligibilityDecision.allow();
        }

        boolean mustPerformGroundItemsCheck =
            context.isMustEnforceStrictPolicies() || (context.getGameRules() != null
                && context.getGameRules().isRestrictGroundItems());
        if (mustPerformGroundItemsCheck) {
            MessageKey mk = widgetTargetOnGroundItem
                ? MessageKey.GROUND_ITEM_CAST_RESTRICTION
                : MessageKey.GROUND_ITEM_TAKE_RESTRICTION;
            return EligibilityDecision.deny(mk);
        }
        return EligibilityDecision.allow();
    }

    private GetClickedTileItemOutput getClickedTileItem(MenuOptionClicked event) {
        return getClickedTileItemFromScene(event.getParam0(), event.getParam1(), event.getId());
    }

    private GetClickedTileItemOutput getClickedTileItemFromScene(int sceneX, int sceneY, int itemId) {
        WorldView worldView = client.getTopLevelWorldView();
        int plane = worldView.getPlane();

        Scene scene = worldView.getScene();
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

    private void recordPendingLootAttempt(int itemId, Tile tile) {
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);
        int expiresAt = client.getTickCount() + PENDING_LOOT_TICK_WINDOW;
        pendingLootAttemptExpiresAtTick.merge(key, expiresAt, Math::max);
    }

    private void cleanupExpiredLootAttempts(int currentTick) {
        pendingLootAttemptExpiresAtTick.entrySet().removeIf(entry -> entry.getValue() < currentTick);
    }

    private GroundItemOwnedByKey createGroundItemKey(int itemId, Tile tile) {
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);
        return GroundItemOwnedByKey.of(itemId, client.getWorld(), worldView.getId(), worldPoint);
    }

    /**
     * Shared Firebase decrements must run only on the client that registered an allowed Take for this key
     * within {@link #PENDING_LOOT_TICK_WINDOW} ticks, and only while we still have positive tracked quantity.
     */
    static boolean shouldConsumeSharedOwnership(int trackedOwnedQuantity, Integer pendingLootExpiresAtTick,
        int currentTick) {
        if (trackedOwnedQuantity <= 0) {
            return false;
        }
        return pendingLootExpiresAtTick != null && pendingLootExpiresAtTick >= currentTick;
    }

    // Called from scheduler thread - must use clientThread.invoke() for client access
    private void cleanupExpiredGroundItems() {
        clientThread.invoke(() -> {
            ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map
                = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
            if (map == null || map.isEmpty()) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now();
            long accountHash = client.getAccountHash();

            for (Map.Entry<GroundItemOwnedByKey, GroundItemOwnedByData> keyEntry : map.entrySet()) {
                GroundItemOwnedByKey key = keyEntry.getKey();
                GroundItemOwnedByData data = keyEntry.getValue();
                if (data == null) {
                    continue;
                }
                if (data.getAccountHash() != accountHash) {
                    continue;
                }
                if (data.getDespawnsAt().getValue().isAfter(now)) {
                    continue;
                }

                log.debug("Cleaning up expired ground item {}", key);

                groundItemOwnedByDataProvider.deletePile(key).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to clean up expired ground item {}", key, throwable);
                    }
                });
            }
        });
    }

    private void cleanupExpiredGroundItemsForEveryone() {
        log.debug("Cleaning up expired ground items for everyone");

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map
            = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (map == null || map.isEmpty()) {
            log.debug("Ground item owned by map is empty, nothing to clean up");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        for (Map.Entry<GroundItemOwnedByKey, GroundItemOwnedByData> keyEntry : map.entrySet()) {
            GroundItemOwnedByKey key = keyEntry.getKey();
            GroundItemOwnedByData data = keyEntry.getValue();
            if (data == null) {
                continue;
            }
            if (data.getDespawnsAt().getValue().isAfter(now)) {
                continue;
            }

            log.debug(
                "Cleaning up expired ground item {} for account hash: {}",
                key,
                data.getAccountHash()
            );

            groundItemOwnedByDataProvider.deletePile(key).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to clean up expired ground item {}", key, throwable);
                }
            });
        }
    }

    private enum EligibilityAction {
        ALLOW,
        DENY,
        LOADING
    }

    @Value
    private static class EligibilityDecision {
        @NonNull EligibilityAction action;
        MessageKey denyMessageKey;

        static EligibilityDecision allow() {
            return new EligibilityDecision(EligibilityAction.ALLOW, null);
        }

        static EligibilityDecision loading() {
            return new EligibilityDecision(EligibilityAction.LOADING, null);
        }

        static EligibilityDecision deny(MessageKey messageKey) {
            return new EligibilityDecision(EligibilityAction.DENY, messageKey);
        }
    }

    @Value
    private static class GetClickedTileItemOutput {

        @NonNull Tile tile;
        @NonNull TileItem tileItem;
    }
}
