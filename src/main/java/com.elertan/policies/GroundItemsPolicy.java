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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;

/**
 * Ground-item Bronzeman rules and Firebase-backed ownership for group drops.
 * <p>
 * Firebase stores how many of this pile the Bronzeman rules still let you take. Dropping logs (self/group)
 * {@linkplain GroundItemOwnedByDataProvider#addToPileQuantity adds} to that budget.
 * <p>
 * Budget only goes down when there is a recent successful local take intent for that tile/item and we then observe
 * qualifying pile shrink/despawn signals. This avoids burning quota from unrelated mixed-pile churn.
 * Timer expiry and unknown/zero despawn times skip Firebase. Once budget hits {@code 0} the row stays until
 * you drop again.
 * <p>
 * Partial pickups do not shrink the budget until the stack fully leaves via a qualifying pickup despawn; leaving
 * loot on the ground until timer despawn is acceptable — we do not try to mirror stack size in Firebase.
 * <p>
 * {@link TileItem#OWNERSHIP_OTHER} is Jagex/RuneLite's label for another player's drops (overlays often show
 * "OTHER"); {@link TileItem#OWNERSHIP_NONE} is separate. We only grant entitlement from OTHER/NONE when there is
 * a recent local drop intent for that item id (merged-pile fallback).
 */
@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {
    private static final int TAKE_INTENT_TICKS = 3;
    private static final int DROP_INTENT_TICKS = 8;
    private static final Duration TAKE_CLAIM_LEASE_DURATION = Duration.ofMillis(1800);

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

    private ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<GroundItemOwnedByKey, Integer> pendingTakeIntentUntilTickByKey
        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GroundItemOwnedByKey, ActiveTakeClaim> activeTakeClaimsByKey
        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PendingDropIntent> pendingDropIntentByItemId
        = new ConcurrentHashMap<>();

    @Inject
    public GroundItemsPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService) {
        super(accountConfigurationService, gameRulesService, policyService, worldTypeService);
    }

    @Override
    public void startUp() throws Exception {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupExpiredGroundItems, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void shutDown() throws Exception {
        scheduler.shutdownNow();
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
        Tile tile = event.getTile();
        if (tileItem == null || tile == null) {
            return;
        }
        int itemId = tileItem.getId();
        int trustedIncreaseQty;
        if (tileItem.getOwnership() == TileItem.OWNERSHIP_SELF
            || tileItem.getOwnership() == TileItem.OWNERSHIP_GROUP) {
            trustedIncreaseQty = Math.max(1, tileItem.getQuantity());
            consumePendingDropIntentQuantity(itemId, trustedIncreaseQty);
        } else {
            trustedIncreaseQty = claimPendingDropIntentQuantity(itemId, Math.max(1, tileItem.getQuantity()));
            if (trustedIncreaseQty <= 0) {
                return;
            }
        }
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);

        ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap
            = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            log.warn("Ground item spawned for me but groundItemOwnedByMap is null");
            return;
        }

        GroundItemOwnedByData existing = groundItemOwnedByDataProvider.getPile(key);
        upsertTrackedEntitledQuantity(
            key,
            tileItem,
            existing,
            trustedIncreaseQty
        );
    }

    public void onItemDespawned(ItemDespawned event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }
        Tile tile = event.getTile();
        TileItem tileItem = event.getItem();
        if (tile == null || tileItem == null) {
            return;
        }
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);

        if (!hasActiveTakeIntent(key)) {
            return;
        }
        if (!groundItemOwnedByDataProvider.hasEntries(key)) {
            return;
        }
        int removedQty = Math.max(1, tileItem.getQuantity());
        int despawnScheduledTick = tileItem.getDespawnTime();
        deferConsumeOnPickupDespawnOnly(key, removedQty, despawnScheduledTick);
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
        if (tileItem == null || tile == null) {
            return;
        }
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);

        if (delta > 0) {
            boolean selfOrGroup = tileItem.getOwnership() == TileItem.OWNERSHIP_SELF
                || tileItem.getOwnership() == TileItem.OWNERSHIP_GROUP;
            int trustedIncreaseQty;
            if (selfOrGroup) {
                trustedIncreaseQty = delta;
                consumePendingDropIntentQuantity(itemId, delta);
            } else {
                trustedIncreaseQty = claimPendingDropIntentQuantity(itemId, delta);
                if (trustedIncreaseQty <= 0) {
                    return;
                }
            }
            GroundItemOwnedByData existing = groundItemOwnedByDataProvider.getPile(key);
            upsertTrackedEntitledQuantity(key, tileItem, existing, trustedIncreaseQty);
            return;
        }

        if (!groundItemOwnedByDataProvider.hasEntries(key)) {
            return;
        }
        if (!hasActiveTakeIntent(key)) {
            return;
        }

        int tickNow = client.getTickCount();
        int despawnScheduledTick = tileItem.getDespawnTime();
        if (!shouldApplyLootDecrementOnDespawn(despawnScheduledTick, tickNow)) {
            return;
        }

        consumeTrackedQuantity(key, Math.abs(delta), "quantity-changed");
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        String option = event.getMenuOption();
        if (option != null && option.startsWith("Drop")) {
            int itemId = event.getId();
            if (itemId > 0) {
                registerDropIntent(itemId);
            }
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
            resolveWorldView(menuEntry),
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

    private static boolean isTakeIntent(String menuOption, MenuAction menuAction) {
        if (menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM) {
            return false;
        }
        return menuOption != null && menuOption.startsWith("Take");
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
            if (isTakeIntent(event.getMenuOption(), menuAction)) {
                registerTakeIntent(output.getTile(), itemId);
            }
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

        // Merged / woodcut piles often advertise OWNERSHIP_NONE — still enforce Firebase budget when present.
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

            boolean mustPerformGroundItemsCheck =
                context.isMustEnforceStrictPolicies() || (context.getGameRules() != null
                    && context.getGameRules().isRestrictGroundItems());
            if (mustPerformGroundItemsCheck) {
                int entitledQty = groundItemOwnedByDataProvider.getTotalOwnedQuantity(key);
                if (entitledQty <= 0) {
                    MessageKey mk = widgetTargetOnGroundItem
                        ? MessageKey.GROUND_ITEM_CAST_RESTRICTION
                        : MessageKey.GROUND_ITEM_TAKE_RESTRICTION;
                    return EligibilityDecision.deny(mk);
                }
            }
            return EligibilityDecision.allow();
        }

        if (ownership == TileItem.OWNERSHIP_NONE) {
            log.debug("Item '{}' is not owned by anyone, allow take", itemComposition.getName());
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
        MenuEntry menuEntry = event.getMenuEntry();
        return getClickedTileItemFromScene(
            resolveWorldView(menuEntry),
            menuEntry.getParam0(),
            menuEntry.getParam1(),
            event.getId()
        );
    }

    private WorldView resolveWorldView(MenuEntry menuEntry) {
        if (menuEntry == null) {
            return client.getTopLevelWorldView();
        }
        WorldView wv = client.getWorldView(menuEntry.getWorldViewId());
        return wv != null ? wv : client.getTopLevelWorldView();
    }

    private GetClickedTileItemOutput getClickedTileItemFromScene(WorldView worldView, int sceneX, int sceneY,
        int itemId) {
        if (worldView == null) {
            worldView = client.getTopLevelWorldView();
        }
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
            if (ti.getId() != itemId) {
                continue;
            }
            return new GetClickedTileItemOutput(tile, ti);
        }
        return null;
    }

    /**
     * Increase the shared entitled quantity for this pile from trusted spawn signals
     * (self/group ownership or recent local drop intent).
     * <p>
     * Uses a remote read-add-write (CAS) so rapid sequential drops do not lose increments when the local map
     * lags behind Firebase.
     */
    private void upsertTrackedEntitledQuantity(
        GroundItemOwnedByKey key,
        TileItem tileItem,
        GroundItemOwnedByData existing,
        int trustedIncreaseQty
    ) {
        long despawnTimeTicks = tileItem.getDespawnTime() - client.getTickCount();
        Duration despawnDuration = TickUtils.ticksToDuration(despawnTimeTicks);
        OffsetDateTime despawnsAt = OffsetDateTime.now().plus(despawnDuration);
        int safeIncreaseQty = Math.max(1, trustedIncreaseQty);
        GroundItemOwnedByData delta = new GroundItemOwnedByData(
            client.getAccountHash(),
            new ISOOffsetDateTime(despawnsAt),
            safeIncreaseQty,
            existing != null ? existing.getDroppedByPlayerName() : null,
            null
        );

        groundItemOwnedByDataProvider.addToPileQuantity(key, delta).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider addToPileQuantity failed", throwable);
            }
        });
    }

    private void deferConsumeOnPickupDespawnOnly(
        GroundItemOwnedByKey key,
        int removedQty,
        int despawnScheduledTick
    ) {
        if (!groundItemOwnedByDataProvider.hasEntries(key)) {
            return;
        }
        clientThread.invokeLater(() -> {
            if (!accountConfigurationService.isBronzemanEnabled()) {
                return;
            }
            if (groundItemOwnedByDataProvider.getGroundItemOwnedByMap() == null) {
                return;
            }
            if (!groundItemOwnedByDataProvider.hasEntries(key)) {
                return;
            }
            int tickNow = client.getTickCount();
            if (!shouldApplyLootDecrementOnDespawn(despawnScheduledTick, tickNow)) {
                return;
            }
            consumeTrackedQuantity(key, removedQty, "despawn");
        });
    }

    private void registerTakeIntent(Tile tile, int itemId) {
        GroundItemOwnedByKey key = createGroundItemKey(itemId, tile);
        int untilTick = client.getTickCount() + TAKE_INTENT_TICKS;
        pendingTakeIntentUntilTickByKey.put(key, untilTick);

        acquireTakeClaim(key, client.getAccountHash()).whenComplete((acquired, throwable) -> {
            if (throwable != null) {
                log.debug("Failed pre-acquiring take claim for {}", key, throwable);
            }
        });
    }

    private void registerDropIntent(int itemId) {
        int now = client.getTickCount();
        int untilTick = now + DROP_INTENT_TICKS;
        pendingDropIntentByItemId.compute(itemId, (id, pending) -> {
            if (pending == null || now > pending.getUntilTick()) {
                return new PendingDropIntent(untilTick, 1);
            }
            return new PendingDropIntent(
                Math.max(untilTick, pending.getUntilTick()),
                pending.getRemainingQuantity() + 1
            );
        });
    }

    private boolean hasActiveTakeIntent(GroundItemOwnedByKey key) {
        Integer untilTick = pendingTakeIntentUntilTickByKey.get(key);
        if (untilTick == null) {
            return false;
        }
        int now = client.getTickCount();
        if (now > untilTick) {
            pendingTakeIntentUntilTickByKey.remove(key, untilTick);
            activeTakeClaimsByKey.remove(key);
            return false;
        }
        return true;
    }

    private int claimPendingDropIntentQuantity(int itemId, int maxQuantityToClaim) {
        if (maxQuantityToClaim <= 0) {
            return 0;
        }

        int now = client.getTickCount();
        final int[] claimed = {0};
        pendingDropIntentByItemId.compute(itemId, (id, pending) -> {
            if (pending == null || now > pending.getUntilTick()) {
                return null;
            }
            claimed[0] = Math.min(maxQuantityToClaim, pending.getRemainingQuantity());
            int remaining = pending.getRemainingQuantity() - claimed[0];
            if (remaining <= 0) {
                return null;
            }
            return new PendingDropIntent(pending.getUntilTick(), remaining);
        });
        return claimed[0];
    }

    private void consumePendingDropIntentQuantity(int itemId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        claimPendingDropIntentQuantity(itemId, quantity);
    }

    private void consumeTrackedQuantity(GroundItemOwnedByKey key, int removedQty, String reason) {
        if (removedQty <= 0) {
            return;
        }

        long accountHash = client.getAccountHash();
        ActiveTakeClaim activeClaim = getActiveTakeClaimForAccount(key, accountHash);

        CompletableFuture<Boolean> consumeFuture;
        if (activeClaim != null) {
            consumeFuture = groundItemOwnedByDataProvider.consumeQuantityWithTakeClaim(
                key,
                removedQty,
                accountHash,
                activeClaim.getClaimId()
            ).thenCompose(consumed -> {
                if (consumed) {
                    return CompletableFuture.completedFuture(true);
                }
                activeTakeClaimsByKey.remove(key, activeClaim);
                return consumeWithExistingOrNewClaim(key, removedQty, accountHash);
            });
        } else {
            consumeFuture = consumeWithExistingOrNewClaim(key, removedQty, accountHash);
        }

        consumeFuture.whenComplete((consumed, throwable) -> {
            if (throwable != null) {
                log.error("consumeQuantity failed on {} {}", reason, key, throwable);
                return;
            }

            if (!Boolean.TRUE.equals(consumed)) {
                log.debug("Skipped consume on {} {} because take claim was unavailable", reason, key);
                return;
            }

            pendingTakeIntentUntilTickByKey.remove(key);
            activeTakeClaimsByKey.remove(key);
        });
    }

    private CompletableFuture<Boolean> consumeWithExistingOrNewClaim(
        GroundItemOwnedByKey key,
        int removedQty,
        long accountHash
    ) {
        if (groundItemOwnedByDataProvider.hasActiveTakeClaimForAccount(key, accountHash)) {
            return groundItemOwnedByDataProvider.consumeQuantityWithTakeClaim(
                key,
                removedQty,
                accountHash,
                null
            ).thenCompose(consumed -> {
                if (consumed) {
                    return CompletableFuture.completedFuture(true);
                }

                activeTakeClaimsByKey.remove(key);
                return acquireTakeClaim(key, accountHash).thenCompose(acquired -> {
                    if (!acquired) {
                        return CompletableFuture.completedFuture(false);
                    }

                    ActiveTakeClaim refreshed = getActiveTakeClaimForAccount(key, accountHash);
                    if (refreshed == null) {
                        return CompletableFuture.completedFuture(false);
                    }

                    return groundItemOwnedByDataProvider.consumeQuantityWithTakeClaim(
                        key,
                        removedQty,
                        accountHash,
                        refreshed.getClaimId()
                    );
                });
            });
        }

        return acquireTakeClaim(key, accountHash).thenCompose(acquired -> {
            if (!acquired) {
                return CompletableFuture.completedFuture(false);
            }

            ActiveTakeClaim claim = getActiveTakeClaimForAccount(key, accountHash);
            if (claim == null) {
                return CompletableFuture.completedFuture(false);
            }

            return groundItemOwnedByDataProvider.consumeQuantityWithTakeClaim(
                key,
                removedQty,
                accountHash,
                claim.getClaimId()
            );
        });
    }

    private CompletableFuture<Boolean> acquireTakeClaim(GroundItemOwnedByKey key, long accountHash) {
        ActiveTakeClaim existing = getActiveTakeClaimForAccount(key, accountHash);
        if (existing != null) {
            return CompletableFuture.completedFuture(true);
        }

        OffsetDateTime claimExpiresAt = OffsetDateTime.now().plus(TAKE_CLAIM_LEASE_DURATION);
        ISOOffsetDateTime leaseExpiry = new ISOOffsetDateTime(claimExpiresAt);
        String claimId = UUID.randomUUID().toString();

        return groundItemOwnedByDataProvider.tryAcquireTakeClaim(key, accountHash, leaseExpiry, claimId)
            .thenApply(acquired -> {
                if (!acquired) {
                    return false;
                }
                activeTakeClaimsByKey.put(key, new ActiveTakeClaim(accountHash, claimId, claimExpiresAt));
                return true;
            });
    }

    private ActiveTakeClaim getActiveTakeClaimForAccount(GroundItemOwnedByKey key, long accountHash) {
        ActiveTakeClaim claim = activeTakeClaimsByKey.get(key);
        if (claim == null) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!claim.isActiveForAccount(accountHash, now)) {
            activeTakeClaimsByKey.remove(key, claim);
            return null;
        }
        return claim;
    }

    /**
     * Only treat {@link ItemDespawned} as a player loot removal when the tile item left before its scheduled
     * despawn tick. If the scheduled tick is unknown ({@code <= 0}), we skip — mixed piles often clear the timer
     * or report bogus values, and consuming then would burn your budget when someone else's stack vanishes.
     */
    private static boolean shouldApplyLootDecrementOnDespawn(int despawnScheduledTick, int tickNow) {
        if (despawnScheduledTick <= 0) {
            return false;
        }
        return tickNow < despawnScheduledTick;
    }

    private GroundItemOwnedByKey createGroundItemKey(int itemId, Tile tile) {
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);
        return GroundItemOwnedByKey.of(itemId, client.getWorld(), worldView.getId(), worldPoint);
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
                if (hasActiveTakeIntent(key)) {
                    continue;
                }

                activeTakeClaimsByKey.remove(key);

                log.debug("Cleaning up expired ground item {}", key);

                groundItemOwnedByDataProvider.deletePile(key).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to clean up expired ground item {}", key, throwable);
                    }
                });
            }
        });
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

    @Value
    private static class ActiveTakeClaim {
        long accountHash;
        @NonNull String claimId;
        @NonNull OffsetDateTime expiresAt;

        boolean isActiveForAccount(long expectedAccountHash, OffsetDateTime now) {
            return accountHash == expectedAccountHash && expiresAt.isAfter(now);
        }
    }

    @Value
    private static class PendingDropIntent {
        int untilTick;
        int remainingQuantity;
    }
}
