package com.elertan;

import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.data.AbstractDataProvider;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.*;
import com.elertan.overlays.ItemUnlockOverlay;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemStack;

import com.elertan.utils.Subscription;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.elertan.utils.AsyncUtils.addErrorLogging;
import static com.elertan.utils.AsyncUtils.withErrorLogging;

@Slf4j
@Singleton
public class ItemUnlockService implements BUPluginLifecycle {

    public static final Set<Integer> AUTO_UNLOCKED_ITEMS = ImmutableSet.of(
        // Bond
        ItemID.OSRS_BOND,
        // All variations of coins
        ItemID.COINS,
        ItemID.COINS_1,
        ItemID.COINS_2,
        ItemID.COINS_3,
        ItemID.COINS_4,
        ItemID.COINS_5,
        ItemID.COINS_25,
        ItemID.COINS_100,
        ItemID.COINS_250,
        ItemID.COINS_1000,
        ItemID.COINS_10000,
        // Platinum token
        ItemID.PLATINUM
    );

    private static final Set<Integer> ITEM_MAPPING_ITEM_IDS = ImmutableSet.of(
        ItemID.ARCEUUS_CORPSE_GOBLIN_INITIAL,
        ItemID.ARCEUUS_CORPSE_MONKEY_INITIAL,
        ItemID.ARCEUUS_CORPSE_IMP_INITIAL,
        ItemID.ARCEUUS_CORPSE_MINOTAUR_INITIAL,
        ItemID.ARCEUUS_CORPSE_SCORPION_INITIAL,
        ItemID.ARCEUUS_CORPSE_BEAR_INITIAL,
        ItemID.ARCEUUS_CORPSE_UNICORN_INITIAL,
        ItemID.ARCEUUS_CORPSE_DOG_INITIAL,
        ItemID.ARCEUUS_CORPSE_CHAOSDRUID_INITIAL,
        ItemID.ARCEUUS_CORPSE_GIANT_INITIAL,
        ItemID.ARCEUUS_CORPSE_OGRE_INITIAL,
        ItemID.ARCEUUS_CORPSE_ELF_INITIAL,
        ItemID.ARCEUUS_CORPSE_TROLL_INITIAL,
        ItemID.ARCEUUS_CORPSE_HORROR_INITIAL,
        ItemID.ARCEUUS_CORPSE_KALPHITE_INITIAL,
        ItemID.ARCEUUS_CORPSE_DAGANNOTH_INITIAL,
        ItemID.ARCEUUS_CORPSE_BLOODVELD_INITIAL,
        ItemID.ARCEUUS_CORPSE_TZHAAR_INITIAL,
        ItemID.ARCEUUS_CORPSE_DEMON_INITIAL,
        ItemID.ARCEUUS_CORPSE_HELLHOUND_INITIAL,
        ItemID.ARCEUUS_CORPSE_AVIANSIE_INITIAL,
        ItemID.ARCEUUS_CORPSE_ABYSSAL_INITIAL,
        ItemID.ARCEUUS_CORPSE_DRAGON_INITIAL
    );

    private static final Map<String, Integer> MAP_ITEM_NAMES = new HashMap<>() {{
        // We need to map clue scrolls to a single item counterpart
        // Because each step has a different item id, and would pollute the item unlocks
        put("Clue scroll (beginner)", ItemID.TRAIL_CLUE_BEGINNER);
        put("Clue scroll (easy)", ItemID.TRAIL_CLUE_EASY_EMOTE001);
        put("Clue scroll (medium)", ItemID.TRAIL_CLUE_MEDIUM_EMOTE001);
        put("Clue scroll (hard)", ItemID.TRAIL_CLUE_HARD_EMOTE001);
        put("Clue scroll (elite)", ItemID.TRAIL_CLUE_ELITE_MUSIC001);
        put("Clue scroll (master)", ItemID.TRAIL_CLUE_MASTER);

        // Same for clue challenge scrolls
        put("Challenge scroll (medium)", ItemID.TRAIL_CLUE_MEDIUM_ANAGRAM001_CHALLENGE);
        put("Challenge scroll (hard)", ItemID.TRAIL_CLUE_HARD_ANAGRAM001_CHALLENGE);
        put("Challenge scroll (elite)", ItemID.TRAIL_ELITE_SKILL_CHALLENGE);

        put("Key (medium)", ItemID.TRAIL_CLUE_MEDIUM_RIDDLE001_KEY);
        put("Key (elite)", ItemID.TRAIL_ELITE_RIDDLE_KEY32);

        put("Loot key", ItemID.WILDY_LOOT_KEY0);

        // Black mask charge variants - all map to uncharged (8921)
        put("Black mask", 8921);
        for (int i = 1; i <= 10; i++) {
            put("Black mask (" + i + ")", 8921);
        }
    }};
    private static final Set<Integer> INCLUDED_CONTAINER_IDS = ImmutableSet.of(
        InventoryID.INV, // inventory
        InventoryID.WORN, // Worn items
        InventoryID.BANK, // bank

        InventoryID.TRAIL_REWARDINV, // Barrows chest
        InventoryID.MISC_RESOURCES_COLLECTED, // Miscellania reward
        InventoryID.RAIDS_REWARDS, // Chambers of eric reward
        InventoryID.TOB_CHESTS, // Theater of Blood reward
        InventoryID.TOA_CHESTS, // Tombs of Amascut reward
        InventoryID.SEED_VAULT, // Farming Guild seed vault
        InventoryID.TRAWLER_REWARDINV, // Fishing trawler reward
        InventoryID.LOOTING_BAG, // Looting bag
        InventoryID.PMOON_REWARDINV // Moons of Peril reward
    );
    private Subscription stateSubscription;
    private Subscription accountConfigSubscription;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ItemManager itemManager;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private UnlockedItemsDataProvider unlockedItemsDataProvider;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ItemUnlockOverlay itemUnlockOverlay;
    @Inject
    private MemberService memberService;
    @Inject
    private GameRulesService gameRulesService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private MinigameService minigameService;
    @Inject
    private CollectionLogService collectionLogService;
    @Inject
    private WorldTypeService worldTypeService;
    private UnlockedItemsDataProvider.UnlockedItemsMapListener unlockedItemsMapListener;
    private volatile boolean hasNotifiedPlayerOfNonSupportedWorldType = false;

    @Override
    public void startUp() throws Exception {
        unlockedItemsMapListener = new UnlockedItemsDataProvider.UnlockedItemsMapListener() {

            @Override
            public void onUpdate(UnlockedItem unlockedItem) {
                // Defer overlay to client thread to:
                // 1. Ensure any collection log chat messages from the same tick are processed first
                // 2. Safely access client.getAccountHash()
                clientThread.invokeLater(() -> {
                    boolean isLocalPlayer = client.getAccountHash() == unlockedItem.getAcquiredByAccountHash();

                    if (isLocalPlayer && collectionLogService.tryConsumeOverlaySuppression(unlockedItem.getName())) {
                        // Suppress overlay - native collection log UI already shows it
                        return;
                    }

                    itemUnlockOverlay.enqueueShowUnlock(
                        unlockedItem.getId(),
                        unlockedItem.getAcquiredByAccountHash(),
                        unlockedItem.getDroppedByNPCId()
                    );
                });

                // Chat notification - keep existing code below unchanged
                boolean hideChat = buPluginConfig.hideUnlockChatInMinigames() && minigameService.isInMinigameOrInstance();
                if (buPluginConfig.showItemUnlocksInChat() && !hideChat) {
                    buChatService.getItemIconTagIfEnabled(unlockedItem.getId())
                        .whenComplete((itemIconTag, throwable) -> {
                            if (throwable != null) {
                                log.error("Failed to get item icon tag", throwable);
                                return;
                            }

                            clientThread.invokeLater(() -> {
                                ChatMessageBuilder builder = new ChatMessageBuilder();
                                builder.append("Unlocked item ");
                                if (itemIconTag != null) {
                                    builder.append(buPluginConfig.chatHighlightColor(), itemIconTag);
                                    builder.append(" ");
                                }
                                builder.append(
                                    buPluginConfig.chatItemNameColor(),
                                    unlockedItem.getName()
                                );

                                if (client.getAccountHash()
                                    != unlockedItem.getAcquiredByAccountHash()) {
                                    Member member = memberService.getMemberByAccountHash(
                                        unlockedItem.getAcquiredByAccountHash());

                                    builder.append(" by ");
                                    builder.append(
                                        buPluginConfig.chatPlayerNameColor(),
                                        member.getName()
                                    );
                                }
                                Integer droppedByNpcId = unlockedItem.getDroppedByNPCId();
                                if (droppedByNpcId != null) {
                                    NPCComposition npcComposition = client.getNpcDefinition(
                                        droppedByNpcId);
                                    builder.append(" (drop from ");
                                    builder.append(
                                        buPluginConfig.chatNPCNameColor(),
                                        npcComposition.getName()
                                    );
                                    builder.append(")");
                                }

                                buChatService.sendMessage(builder.build());
                            });
                        }
                    );
                }

            }

            @Override
            public void onDelete(UnlockedItem unlockedItem) {
                // We can consider this re-locking items

                buChatService.getItemIconTagIfEnabled(unlockedItem.getId())
                    .whenComplete((itemIconTag, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to get item icon tag", throwable);
                        return;
                    }

                    ChatMessageBuilder builder = new ChatMessageBuilder();
                    if (itemIconTag != null) {
                        builder.append(buPluginConfig.chatHighlightColor(), itemIconTag);
                        builder.append(" ");
                    }
                    builder.append(buPluginConfig.chatItemNameColor(), unlockedItem.getName());
                    builder.append(" has been removed from unlocked items.");
                    buChatService.sendMessage(builder.build());
                });
            }
        };
        unlockedItemsDataProvider.addUnlockedItemsMapListener(unlockedItemsMapListener);
        stateSubscription = unlockedItemsDataProvider.getState()
            .subscribe(state -> unlockedItemDataProviderStateListener(state));
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        stateSubscription.dispose();
        accountConfigSubscription.dispose();
        unlockedItemsDataProvider.removeUnlockedItemsMapListener(unlockedItemsMapListener);
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGGED_IN) {
            checkAndNotifyNonSupportedWorldType();
        } else if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING) {
            hasNotifiedPlayerOfNonSupportedWorldType = false;
        }
    }

    public void onItemContainerChanged(ItemContainerChanged event) {
        if (unlockedItemsDataProviderNotReady()) {
            return;
        }

        int containerId = event.getContainerId();
        if (!INCLUDED_CONTAINER_IDS.contains(containerId)) {
            return;
        }
        ItemContainer itemContainer = event.getItemContainer();
        unlockItemsFromItemContainer(itemContainer);
    }

    public void onServerNpcLoot(ServerNpcLoot event) {
        if (unlockedItemsDataProviderNotReady()) {
            return;
        }

        int npcId = event.getComposition().getId();
        event.getItems().stream()
            .map(ItemStack::getId)
            .filter(id -> !hasUnlockedItem(id))
            .map(itemId -> unlockItem(itemId, npcId))
            .forEach(addErrorLogging("Failed to unlock item in on server npc loot"));
    }

    public void onItemSpawned(ItemSpawned event) {
        if (unlockedItemsDataProviderNotReady()) {
            return;
        }

        // Check if inventory is full (28 items)
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return;
        }

        Item[] items = inventory.getItems();
        int itemCount = 0;
        for (Item item : items) {
            if (item.getId() > 0) {
                itemCount++;
            }
        }
        if (itemCount < 28) {
            return;
        }

        // Check if item spawned at player's tile
        TileItem tileItem = event.getItem();
        Tile tile = event.getTile();

        // Only unlock items that belong to us (our drops when inventory is full)
        // Ignore items dropped by other players
        int ownership = tileItem.getOwnership();
        if (ownership != TileItem.OWNERSHIP_SELF && ownership != TileItem.OWNERSHIP_GROUP) {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        WorldPoint playerLocation = localPlayer.getWorldLocation();
        WorldPoint itemLocation = tile.getWorldLocation();

        if (!playerLocation.equals(itemLocation)) {
            return;
        }

        int itemId = tileItem.getId();
        if (hasUnlockedItem(itemId)) {
            return;
        }

        withErrorLogging(unlockItem(itemId), "Failed to unlock ground item");
    }

    // Code from: RuneProfile Plugin
    // Repository: https://github.com/ReinhardtR/runeprofile-plugin
    // License: BSD 2-Clause License
    // Unlock all unlocked items from the collection log when the interface is opened
    public void onScriptPreFired(ScriptPreFired preFired) {
        if (preFired.getScriptId() != 4100) {
            return;
        }

        // prevent reacting to scripts fired when opened from adventure log
        // e.g. other plugins might fire the collection log script when viewing other players' collection logs
        boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
        if (isOpenedFromAdventureLog) {
            return;
        }

        int itemId = (int)preFired.getScriptEvent().getArguments()[1];
        withErrorLogging(unlockItem(itemId), "Failed to unlock item in on script pre fired");
    }

    public boolean hasUnlockedItem(int initialItemId) throws IllegalStateException {
        if (unlockedItemsDataProviderNotReady()) {
            throw new IllegalStateException("State is not READY");
        }

        int itemId = canonicalizeItemId(initialItemId);

        if (AUTO_UNLOCKED_ITEMS.contains(itemId)) {
//            log.info("Item with id {} is auto unlocked", itemId);
            return true;
        }

        Map<Integer, UnlockedItem> map = unlockedItemsDataProvider.getUnlockedItemsMap();
        if (map == null) {
            throw new IllegalStateException("Unlocked items map is null");
        }
        return map.containsKey(itemId);
    }

    public CompletableFuture<Void> removeUnlockedItemById(int itemId) {
        boolean hasUnlockedItem;
        try {
            hasUnlockedItem = hasUnlockedItem(itemId);
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
        if (!hasUnlockedItem) {
            log.warn("Attempted to remove unlocked item with id {} but it is not unlocked yet", itemId);
            return CompletableFuture.completedFuture(null);
        }

        return unlockedItemsDataProvider.removeUnlockedItemById(itemId)
            .thenRun(() -> log.debug("Removed unlocked item with id {}", itemId));
    }

    private boolean unlockedItemsDataProviderNotReady() {
        return unlockedItemsDataProvider.getState().get() != UnlockedItemsDataProvider.State.Ready;
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            return;
        }
        checkAndNotifyNonSupportedWorldType();
    }

    private void checkAndNotifyNonSupportedWorldType() {
        boolean isSupported = worldTypeService.isCurrentWorldSupported();

        if (isSupported) {
            return;
        }
        // Because this check can invoked from logging in/hopping to a new world
        // as well as configuring the plugin, we need to make sure we only notify
        // once for an unsupported world type
        if (hasNotifiedPlayerOfNonSupportedWorldType) {
            return;
        }
        hasNotifiedPlayerOfNonSupportedWorldType = true;
        buChatService.sendMessage(chatMessageProvider.messageFor(MessageKey.ITEM_UNLOCKS_UNSUPPORTED_WORLD));
    }

    private CompletableFuture<Void> unlockItem(int initialItemId) {
        return unlockItem(initialItemId, null);
    }

    private CompletableFuture<Void> unlockItem(int initialItemId, Integer droppedByNPCId) {
        if (initialItemId <= 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Item id must be greater than 1"));
        }

        // We don't support all world types, for example we don't want unlocks on seasonal modes
        if (!worldTypeService.isCurrentWorldSupported()) {
            log.debug("Current world is not supported for unlocking items");
            return CompletableFuture.completedFuture(null);
        }

        // Disable LMS unlocks
        if (minigameService.isPlayingLastManStanding()) {
            return CompletableFuture.completedFuture(null);
        }

        // Skip placeholders
        ItemComposition initialItemComposition = client.getItemDefinition(initialItemId);
        // This method returns -1 if the item is NOT a placeholder
        if (initialItemComposition.getPlaceholderTemplateId() != -1) {
            return CompletableFuture.completedFuture(null);
        }

        int itemId;
        try {
            itemId = canonicalizeItemId(initialItemId);

            if (hasUnlockedItem(itemId)) {
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }

        // Get new item definition after canonicalization
        ItemComposition itemComposition = client.getItemDefinition(itemId);
        final boolean fIsTradeable = itemComposition.isTradeable();
        final String fItemName = itemComposition.getName();
        final int fItemId = itemId;
        // Cache accountHash before async call - client methods require client thread
        final long acquiredByAccountHash = client.getAccountHash();
        return gameRulesService
            .waitUntilGameRulesReady(null)
            .thenCompose(__ -> {
                GameRules gameRules = gameRulesService.getGameRules().get();
                log.debug(
                    "is only for traded items: {} - is tradeable: {}",
                    gameRules.isOnlyForTradeableItems(),
                    fIsTradeable
                );
                if (gameRules.isOnlyForTradeableItems() && !fIsTradeable) {
                    return CompletableFuture.completedFuture(null);
                }

                ISOOffsetDateTime acquiredAt = new ISOOffsetDateTime(OffsetDateTime.now());

                UnlockedItem unlockedItem = new UnlockedItem(
                    fItemId,
                    fItemName,
                    acquiredByAccountHash,
                    acquiredAt,
                    droppedByNPCId
                );
                log.debug("Unlocked item ({}) '{}'", fItemId, fItemName);
                return unlockedItemsDataProvider.addUnlockedItem(unlockedItem);
            });
    }

    private int canonicalizeItemId(int initialItemId) {
        // We want the base item, not a noted item or similar
        int itemId = itemManager.canonicalize(initialItemId);

        // If necessary, we also need to map the item to a different one
        // for example ensouled heads have multiple variations of the same item
        // one that you can re-animate, and one you cannot.
        // We don't want to unlock these multiple times
        if (ITEM_MAPPING_ITEM_IDS.contains(itemId)) {
            Collection<ItemMapping> mappings = ItemMapping.map(itemId);
            if (mappings == null || mappings.isEmpty()) {
                throw new RuntimeException("Failed to map item id " + itemId);
            }
            final ItemMapping mapping = mappings.stream().findFirst().get();
            itemId = mapping.getTradeableItem();
        }

        // If necessary, we also need to map the item to a different one by name
        // for example clue scrolls have like 50 variations, but they're
        // essentially the same item
        String itemName = client.getItemDefinition(itemId).getName();
        return MAP_ITEM_NAMES.getOrDefault(itemName, itemId);
    }

    private void unlockedItemDataProviderStateListener(AbstractDataProvider.State state) {
        if (state != AbstractDataProvider.State.Ready) {
            return;
        }
//        if (hasUnlockedItemDataProviderReadyStateBeenSeen) {
//            return;
//        }
//        hasUnlockedItemDataProviderReadyStateBeenSeen = true;

        clientThread.invokeLater(() -> {
            Map<Integer, UnlockedItem> map = unlockedItemsDataProvider.getUnlockedItemsMap();
            if (map == null) {
                throw new IllegalStateException("Unlocked items map is null");
            }
            int unlockedItemsSize = map.size();
            buChatService.sendMessage(String.format(
                "Loaded with %d unlocked items.",
                unlockedItemsSize
            ));

            // This is the first time the unlocked items are ready
            log.debug(
                "Unlocked items data provider ready for item unlock service first time, checking inventory");
            INCLUDED_CONTAINER_IDS.stream()
                .map(client::getItemContainer)
                .forEach(this::unlockItemsFromItemContainer);
        });
    }

    private void unlockItemsFromItemContainer(ItemContainer itemContainer) {
        if (unlockedItemsDataProviderNotReady()) {
            return;
        }

        if (itemContainer == null) {
            return;
        }

        Arrays.stream(itemContainer.getItems())
            .filter(Objects::nonNull)
            .filter(item -> item.getQuantity() > 0)
            .map(Item::getId)
            .filter(id -> !hasUnlockedItem(id))
            .map(this::unlockItem)
            .forEach(addErrorLogging("Failed to unlock item in item container changed"));
    }
}
