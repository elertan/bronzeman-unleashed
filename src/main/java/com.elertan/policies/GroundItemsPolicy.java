package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
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
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;

@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private BUPluginConfig buPluginConfig;
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
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService) {
        super(accountConfigurationService, gameRulesService, policyService, worldTypeService);
    }

    @Override
    public void startUp() throws Exception {
        groundItemOwnedByDataProviderListener = new GroundItemOwnedByDataProvider.Listener() {
            @Override
            public void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map) {
            }

            @Override
            public void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value) {
            }

            @Override
            public void onRemove(GroundItemOwnedByKey key, String entryKey) {
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
            // item does not belong to me, ignore it
            return;
        }
        Tile tile = event.getTile();
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = GroundItemOwnedByKey.of(itemId, client.getWorld(), worldView.getId(), worldPoint);

        ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> groundItemOwnedByMap = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            log.warn("Ground item spawned for me but groundItemOwnedByMap is null");
            return;
        }

        long despawnTimeTicks = tileItem.getDespawnTime() - client.getTickCount();
        Duration despawnDuration = TickUtils.ticksToDuration(despawnTimeTicks);
        OffsetDateTime despawnsAt = OffsetDateTime.now().plus(despawnDuration);
        GroundItemOwnedByData newGroundItemOwnedByData = new GroundItemOwnedByData(
            client.getAccountHash(),
            new ISOOffsetDateTime(despawnsAt),
            tileItem.getQuantity(),
            null
        );

        groundItemOwnedByDataProvider.addEntry(key, newGroundItemOwnedByData)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider addEntry failed", throwable);
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
            // item does not belong to me, ignore it
            return;
        }
        Tile tile = event.getTile();
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = GroundItemOwnedByKey.of(itemId, client.getWorld(), worldView.getId(), worldPoint);

        if (!groundItemOwnedByDataProvider.hasEntries(key)) {
            log.debug("gi {} has no entries, ignore", key);
            return;
        }

        groundItemOwnedByDataProvider.consumeQuantity(key, tileItem.getQuantity()).whenComplete((result, throwable) -> {
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
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF
            && tileItem.getOwnership() != TileItem.OWNERSHIP_GROUP) {
            return;
        }

        Tile tile = event.getTile();
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);
        int itemId = tileItem.getId();
        GroundItemOwnedByKey key = GroundItemOwnedByKey.of(itemId, client.getWorld(), worldView.getId(), worldPoint);

        if (delta > 0) {
            // Track increases as additional owned quantity for this pile.
            long despawnTimeTicks = tileItem.getDespawnTime() - client.getTickCount();
            Duration despawnDuration = TickUtils.ticksToDuration(despawnTimeTicks);
            OffsetDateTime despawnsAt = OffsetDateTime.now().plus(despawnDuration);
            GroundItemOwnedByData data = new GroundItemOwnedByData(
                client.getAccountHash(),
                new ISOOffsetDateTime(despawnsAt),
                delta,
                null
            );
            groundItemOwnedByDataProvider.addEntry(key, data).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider addEntry failed", throwable);
                }
            });
            return;
        }

        groundItemOwnedByDataProvider.consumeQuantity(key, Math.abs(delta))
            .whenComplete((result, throwable) -> {
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

        PolicyContext context = createContext();
        if (!shouldDeprioritizeUnlootableMenuEntries(context)) {
            return;
        }

        MenuEntry menuEntry = event.getMenuEntry();
        if (menuEntry == null) {
            return;
        }
        if (!isGroundItemAction(menuEntry.getType())) {
            return;
        }

        String option = menuEntry.getOption();
        if (!"Take".equals(option) && !"Cast".equals(option)) {
            return;
        }

        int itemId = menuEntry.getIdentifier();
        if (itemId <= 1) {
            return;
        }

        GetClickedTileItemOutput output = getClickedTileItem(
            menuEntry.getParam0(),
            menuEntry.getParam1(),
            itemId
        );
        if (output == null) {
            return;
        }

        EligibilityDecision decision = evaluateTakeEligibility(context, itemId, output.getTile(), output.getTileItem());
        if (decision.getAction() == EligibilityAction.DENY) {
            menuEntry.setDeprioritized(true);
        }
    }

    private void enforceItemTakePolicyWhereNecessary(MenuOptionClicked event,
        PolicyContext context) {
        MenuAction menuAction = event.getMenuAction();
        if (!isGroundItemAction(menuAction)) {
            return;
        }

        String menuOption = event.getMenuOption();
        if (!menuOption.equals("Take") && !menuOption.equals("Cast")) {
            return;
        }
        int itemId = event.getId();
        if (itemId <= 1) {
            return;
        }

        GetClickedTileItemOutput output = getClickedTileItem(event.getParam0(), event.getParam1(), itemId);
        if (output == null) {
            log.warn(
                "Ground item not found at scene ({}, {}) for id {}",
                event.getParam0(),
                event.getParam1(),
                itemId
            );
            return;
        }
        EligibilityDecision decision = evaluateTakeEligibility(
            context,
            itemId,
            output.getTile(),
            output.getTileItem()
        );
        if (decision.getAction() == EligibilityAction.ALLOW) {
            return;
        }

        if (decision.getAction() == EligibilityAction.LOADING) {
            event.consume();
            buChatService.sendErrorMessage(chatMessageProvider.messageFor(
                MessageKey.STILL_LOADING_PLEASE_WAIT));
            return;
        }

        if (decision.getMessageKey() != null) {
            event.consume();
            MessageKey messageKey = decision.getMessageKey();
            if (messageKey == MessageKey.GROUND_ITEM_TAKE_RESTRICTION
                || messageKey == MessageKey.GROUND_ITEM_CAST_RESTRICTION) {
                messageKey = menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
                    ? MessageKey.GROUND_ITEM_CAST_RESTRICTION
                    : MessageKey.GROUND_ITEM_TAKE_RESTRICTION;
            }
            buChatService.sendRestrictionMessage(messageKey);
        }
    }

    private boolean shouldDeprioritizeUnlootableMenuEntries(PolicyContext context) {
        if (context.isMustEnforceStrictPolicies()) {
            return true;
        }
        GameRules rules = context.getGameRules();
        return rules != null && rules.isRestrictGroundItems()
            && rules.isDeprioritizeUnlootableGroundItems();
    }

    private boolean isGroundItemAction(MenuAction menuAction) {
        return menuAction.ordinal() >= MenuAction.GROUND_ITEM_FIRST_OPTION.ordinal()
            && menuAction.ordinal() <= MenuAction.GROUND_ITEM_FIFTH_OPTION.ordinal()
            || menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
    }

    private EligibilityDecision evaluateTakeEligibility(PolicyContext context, int itemId, Tile tile,
        TileItem tileItem) {
        // In last man standing we want to allow taking any items.
        if (minigameService.isPlayingLastManStanding()) {
            return EligibilityDecision.allow();
        }

        ItemComposition itemComposition = client.getItemDefinition(itemId);
        int ownership = tileItem.getOwnership();
        if (ownership == TileItem.OWNERSHIP_NONE) {
            log.debug("Item '{}' is not owned by anyone, allow take", itemComposition.getName());
            return EligibilityDecision.allow();
        }

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView worldView = client.findWorldViewFromWorldPoint(worldPoint);
        GroundItemOwnedByKey key = GroundItemOwnedByKey.of(
            itemId,
            client.getWorld(),
            worldView.getId(),
            worldPoint
        );

        ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> groundItemOwnedByMap
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

        int trackedOwnedQuantity = groundItemOwnedByDataProvider.getTotalOwnedQuantity(key);
        if (trackedOwnedQuantity > 0) {
            boolean mustPerformPlayerVersusPlayerCheck =
                context.isMustEnforceStrictPolicies() || (context.getGameRules() != null
                    && context.getGameRules().isRestrictPlayerVersusPlayerLoot());

            if (mustPerformPlayerVersusPlayerCheck) {
                ConcurrentHashMap<String, GroundItemOwnedByData> entries = groundItemOwnedByDataProvider.getEntries(key);
                if (entries != null) {
                    for (GroundItemOwnedByData data : entries.values()) {
                        String droppedByPlayerName = data.getDroppedByPlayerName();
                        if (droppedByPlayerName == null) {
                            continue;
                        }

                        Member member = null;
                        try {
                            member = memberService.getMemberByName(droppedByPlayerName);
                        } catch (Exception ignored) {
                        }
                        if (member == null) {
                            log.debug("Player '{}' is not part of our group, deny take", droppedByPlayerName);
                            return EligibilityDecision.deny(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_RESTRICTION);
                        }
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
            return EligibilityDecision.deny(MessageKey.GROUND_ITEM_TAKE_RESTRICTION);
        }
        return EligibilityDecision.allow();
    }

    private GetClickedTileItemOutput getClickedTileItem(int sceneX, int sceneY, int itemId) {
        WorldView worldView = client.getTopLevelWorldView();
        final int plane = worldView.getPlane();

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

    // Called from scheduler thread - must use clientThread.invoke() for client access
    private void cleanupExpiredGroundItems() {
        clientThread.invoke(() -> {
            ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
            if (map == null || map.isEmpty()) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now();
            long accountHash = client.getAccountHash();

            for (Map.Entry<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> keyEntry : map.entrySet()) {
                GroundItemOwnedByKey key = keyEntry.getKey();
                ConcurrentHashMap<String, GroundItemOwnedByData> entries = keyEntry.getValue();
                if (entries == null) {
                    continue;
                }

                for (Map.Entry<String, GroundItemOwnedByData> entry : entries.entrySet()) {
                    String entryKey = entry.getKey();
                    GroundItemOwnedByData data = entry.getValue();
                    if (data.getAccountHash() != accountHash) {
                        continue;
                    }
                    if (data.getDespawnsAt().getValue().isAfter(now)) {
                        continue;
                    }

                    log.debug("Cleaning up expired ground item {} entry {}", key, entryKey);

                    groundItemOwnedByDataProvider.removeEntry(key, entryKey).whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to clean up expired ground item {} entry {}", key, entryKey, throwable);
                        }
                    });
                }
            }
        });
    }

    private void cleanupExpiredGroundItemsForEveryone() {
        log.debug("Cleaning up expired ground items for everyone");

        ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (map == null || map.isEmpty()) {
            log.debug("Ground item owned by map is empty, nothing to clean up");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        for (Map.Entry<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> keyEntry : map.entrySet()) {
            GroundItemOwnedByKey key = keyEntry.getKey();
            ConcurrentHashMap<String, GroundItemOwnedByData> entries = keyEntry.getValue();
            if (entries == null) {
                continue;
            }

            for (Map.Entry<String, GroundItemOwnedByData> entry : entries.entrySet()) {
                String entryKey = entry.getKey();
                GroundItemOwnedByData data = entry.getValue();
                if (data.getDespawnsAt().getValue().isAfter(now)) {
                    continue;
                }

                log.debug(
                    "Cleaning up expired ground item {} entry {} for account hash: {}",
                    key,
                    entryKey,
                    data.getAccountHash()
                );

                groundItemOwnedByDataProvider.removeEntry(key, entryKey).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to clean up expired ground item {} entry {}", key, entryKey, throwable);
                    }
                });
            }
        }
    }

    private enum EligibilityAction {
        ALLOW,
        DENY,
        LOADING
    }

    @Value
    private static class EligibilityDecision {

        EligibilityAction action;
        MessageKey messageKey;

        static EligibilityDecision allow() {
            return new EligibilityDecision(EligibilityAction.ALLOW, null);
        }

        static EligibilityDecision deny(MessageKey messageKey) {
            return new EligibilityDecision(EligibilityAction.DENY, messageKey);
        }

        static EligibilityDecision loading() {
            return new EligibilityDecision(EligibilityAction.LOADING, MessageKey.STILL_LOADING_PLEASE_WAIT);
        }
    }

    @Value
    private static class GetClickedTileItemOutput {

        @NonNull Tile tile;
        @NonNull TileItem tileItem;
    }
}
