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
import net.runelite.client.game.WorldService;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
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
        ItemID.OSRS_BOND, ItemID.COINS, ItemID.COINS_1, ItemID.COINS_2, ItemID.COINS_3,
        ItemID.COINS_4, ItemID.COINS_5, ItemID.COINS_25, ItemID.COINS_100,
        ItemID.COINS_250, ItemID.COINS_1000, ItemID.COINS_10000, ItemID.PLATINUM);

    private static final Set<Integer> ITEM_MAPPING_ITEM_IDS = ImmutableSet.of(
        ItemID.ARCEUUS_CORPSE_GOBLIN_INITIAL, ItemID.ARCEUUS_CORPSE_MONKEY_INITIAL,
        ItemID.ARCEUUS_CORPSE_IMP_INITIAL, ItemID.ARCEUUS_CORPSE_MINOTAUR_INITIAL,
        ItemID.ARCEUUS_CORPSE_SCORPION_INITIAL, ItemID.ARCEUUS_CORPSE_BEAR_INITIAL,
        ItemID.ARCEUUS_CORPSE_UNICORN_INITIAL, ItemID.ARCEUUS_CORPSE_DOG_INITIAL,
        ItemID.ARCEUUS_CORPSE_CHAOSDRUID_INITIAL, ItemID.ARCEUUS_CORPSE_GIANT_INITIAL,
        ItemID.ARCEUUS_CORPSE_OGRE_INITIAL, ItemID.ARCEUUS_CORPSE_ELF_INITIAL,
        ItemID.ARCEUUS_CORPSE_TROLL_INITIAL, ItemID.ARCEUUS_CORPSE_HORROR_INITIAL,
        ItemID.ARCEUUS_CORPSE_KALPHITE_INITIAL, ItemID.ARCEUUS_CORPSE_DAGANNOTH_INITIAL,
        ItemID.ARCEUUS_CORPSE_BLOODVELD_INITIAL, ItemID.ARCEUUS_CORPSE_TZHAAR_INITIAL,
        ItemID.ARCEUUS_CORPSE_DEMON_INITIAL, ItemID.ARCEUUS_CORPSE_HELLHOUND_INITIAL,
        ItemID.ARCEUUS_CORPSE_AVIANSIE_INITIAL, ItemID.ARCEUUS_CORPSE_ABYSSAL_INITIAL,
        ItemID.ARCEUUS_CORPSE_DRAGON_INITIAL);

    private static final Map<String, Integer> MAP_ITEM_NAMES = new HashMap<>();
    static {
        MAP_ITEM_NAMES.put("Clue scroll (beginner)", ItemID.TRAIL_CLUE_BEGINNER);
        MAP_ITEM_NAMES.put("Clue scroll (easy)", ItemID.TRAIL_CLUE_EASY_EMOTE001);
        MAP_ITEM_NAMES.put("Clue scroll (medium)", ItemID.TRAIL_CLUE_MEDIUM_EMOTE001);
        MAP_ITEM_NAMES.put("Clue scroll (hard)", ItemID.TRAIL_CLUE_HARD_EMOTE001);
        MAP_ITEM_NAMES.put("Clue scroll (elite)", ItemID.TRAIL_CLUE_ELITE_MUSIC001);
        MAP_ITEM_NAMES.put("Clue scroll (master)", ItemID.TRAIL_CLUE_MASTER);
        MAP_ITEM_NAMES.put("Challenge scroll (medium)", ItemID.TRAIL_CLUE_MEDIUM_ANAGRAM001_CHALLENGE);
        MAP_ITEM_NAMES.put("Challenge scroll (hard)", ItemID.TRAIL_CLUE_HARD_ANAGRAM001_CHALLENGE);
        MAP_ITEM_NAMES.put("Challenge scroll (elite)", ItemID.TRAIL_ELITE_SKILL_CHALLENGE);
        MAP_ITEM_NAMES.put("Key (medium)", ItemID.TRAIL_CLUE_MEDIUM_RIDDLE001_KEY);
        MAP_ITEM_NAMES.put("Key (elite)", ItemID.TRAIL_ELITE_RIDDLE_KEY32);
        MAP_ITEM_NAMES.put("Loot key", ItemID.WILDY_LOOT_KEY0);
        MAP_ITEM_NAMES.put("Black mask", 8921);
        for (int i = 1; i <= 10; i++) MAP_ITEM_NAMES.put("Black mask (" + i + ")", 8921);
    }

    private static final Set<Integer> INCLUDED_CONTAINER_IDS = ImmutableSet.of(
        InventoryID.INV, InventoryID.WORN, InventoryID.BANK, InventoryID.TRAIL_REWARDINV,
        InventoryID.MISC_RESOURCES_COLLECTED, InventoryID.RAIDS_REWARDS, InventoryID.TOB_CHESTS,
        InventoryID.TOA_CHESTS, InventoryID.SEED_VAULT, InventoryID.TRAWLER_REWARDINV,
        InventoryID.LOOTING_BAG, InventoryID.PMOON_REWARDINV);

    private static final Set<WorldType> supportedWorldTypes = ImmutableSet.of(
        WorldType.MEMBERS, WorldType.PVP, WorldType.SKILL_TOTAL,
        WorldType.HIGH_RISK, WorldType.FRESH_START_WORLD, WorldType.LAST_MAN_STANDING);

    private Subscription stateSubscription;
    private Subscription accountConfigSubscription;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private WorldService worldService;
    @Inject private ItemManager itemManager;
    @Inject private BUPluginConfig buPluginConfig;
    @Inject private UnlockedItemsDataProvider unlockedItemsDataProvider;
    @Inject private BUChatService buChatService;
    @Inject private ItemUnlockOverlay itemUnlockOverlay;
    @Inject private MemberService memberService;
    @Inject private GameRulesService gameRulesService;
    @Inject private ChatMessageProvider chatMessageProvider;
    @Inject private AccountConfigurationService accountConfigurationService;
    @Inject private MinigameService minigameService;
    @Inject private CollectionLogService collectionLogService;
    private UnlockedItemsDataProvider.UnlockedItemsMapListener unlockedItemsMapListener;
    private volatile boolean hasNotifiedPlayerOfNonSupportedWorldType = false;
    private volatile boolean suppressDeleteNotifications = false;
    private volatile boolean ignoreHasUnlockedDuringReset = false;

    static int canonicalizeItemId(int initialItemId, ItemManager itemManager, Client client) {
        int itemId = itemManager.canonicalize(initialItemId);
        if (ITEM_MAPPING_ITEM_IDS.contains(itemId)) {
            Collection<ItemMapping> mappings = ItemMapping.map(itemId);
            if (mappings == null || mappings.isEmpty()) throw new RuntimeException("Failed to map item id " + itemId);
            itemId = mappings.stream().findFirst().get().getTradeableItem();
        }
        return MAP_ITEM_NAMES.getOrDefault(client.getItemDefinition(itemId).getName(), itemId);
    }

    private void appendItemIcon(ChatMessageBuilder b, String iconTag) {
        if (iconTag != null) {
            b.append(buPluginConfig.chatHighlightColor(), iconTag);
            b.append(" ");
        }
    }

    @Override
    public void startUp() throws Exception {
        unlockedItemsMapListener = new UnlockedItemsDataProvider.UnlockedItemsMapListener() {
            @Override
            public void onUpdate(UnlockedItem unlockedItem) {
                clientThread.invokeLater(() -> {
                    boolean isLocalPlayer = client.getAccountHash() == unlockedItem.getAcquiredByAccountHash();
                    if (isLocalPlayer && collectionLogService.tryConsumeOverlaySuppression(unlockedItem.getName())) return;
                    itemUnlockOverlay.enqueueShowUnlock(
                        unlockedItem.getId(), unlockedItem.getAcquiredByAccountHash(), unlockedItem.getDroppedByNPCId());
                });

                boolean hideChat = buPluginConfig.hideUnlockChatInMinigames() && minigameService.isInMinigameOrInstance();
                if (!buPluginConfig.showItemUnlocksInChat() || hideChat) return;

                buChatService.getItemIconTagIfEnabled(unlockedItem.getId()).whenComplete((iconTag, t) -> {
                    if (t != null) { log.error("Failed to get item icon tag", t); return; }
                    clientThread.invokeLater(() -> {
                        ChatMessageBuilder b = new ChatMessageBuilder();
                        b.append("Unlocked item ");
                        appendItemIcon(b, iconTag);
                        b.append(buPluginConfig.chatItemNameColor(), unlockedItem.getName());
                        if (client.getAccountHash() != unlockedItem.getAcquiredByAccountHash()) {
                            Member member = memberService.getMemberByAccountHash(unlockedItem.getAcquiredByAccountHash());
                            b.append(" by ");
                            b.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                        }
                        Integer npcId = unlockedItem.getDroppedByNPCId();
                        if (npcId != null) {
                            b.append(" (drop from ");
                            b.append(buPluginConfig.chatNPCNameColor(), client.getNpcDefinition(npcId).getName());
                            b.append(")");
                        }
                        buChatService.sendMessage(b.build());
                    });
                });
            }

            @Override
            public void onDelete(UnlockedItem unlockedItem) {
                if (suppressDeleteNotifications) {
                    return;
                }
                buChatService.getItemIconTagIfEnabled(unlockedItem.getId()).whenComplete((iconTag, t) -> {
                    if (t != null) { log.error("Failed to get item icon tag", t); return; }
                    ChatMessageBuilder b = new ChatMessageBuilder();
                    appendItemIcon(b, iconTag);
                    b.append(buPluginConfig.chatItemNameColor(), unlockedItem.getName());
                    b.append(" has been removed from unlocked items.");
                    buChatService.sendMessage(b.build());
                });
            }
        };
        unlockedItemsDataProvider.addUnlockedItemsMapListener(unlockedItemsMapListener);
        stateSubscription = unlockedItemsDataProvider.getState()
            .subscribe(this::unlockedItemDataProviderStateListener);
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
            || accountConfigurationService.getCurrentAccountConfiguration() == null) return;
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGGED_IN) checkAndNotifyNonSupportedWorldType();
        else if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
            hasNotifiedPlayerOfNonSupportedWorldType = false;
    }

    public void onItemContainerChanged(ItemContainerChanged event) {
        if (unlockedItemsDataProviderNotReady()) return;
        if (!INCLUDED_CONTAINER_IDS.contains(event.getContainerId())) return;
        unlockItemsFromItemContainer(event.getItemContainer());
    }

    public void onServerNpcLoot(ServerNpcLoot event) {
        if (unlockedItemsDataProviderNotReady()) return;
        int npcId = event.getComposition().getId();
        event.getItems().stream()
            .map(ItemStack::getId)
            .filter(id -> !hasUnlockedItem(id))
            .map(itemId -> unlockItem(itemId, npcId))
            .forEach(addErrorLogging("Failed to unlock item in on server npc loot"));
    }

    public void onItemSpawned(ItemSpawned event) {
        if (unlockedItemsDataProviderNotReady()) return;
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) return;
        if (Arrays.stream(inventory.getItems()).filter(i -> i.getId() > 0).count() < 28) return;

        TileItem tileItem = event.getItem();
        int ownership = tileItem.getOwnership();
        if (ownership != TileItem.OWNERSHIP_SELF && ownership != TileItem.OWNERSHIP_GROUP) return;

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return;
        if (!localPlayer.getWorldLocation().equals(event.getTile().getWorldLocation())) return;

        int itemId = tileItem.getId();
        if (!hasUnlockedItem(itemId))
            withErrorLogging(unlockItem(itemId), "Failed to unlock ground item");
    }

    public void onScriptPreFired(ScriptPreFired preFired) {
        if (preFired.getScriptId() != 4100) return;
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1) return;
        int itemId = (int) preFired.getScriptEvent().getArguments()[1];
        withErrorLogging(unlockItem(itemId), "Failed to unlock item in on script pre fired");
    }

    public boolean hasUnlockedItem(int initialItemId) throws IllegalStateException {
        if (ignoreHasUnlockedDuringReset) {
            return false;
        }
        if (unlockedItemsDataProviderNotReady()) throw new IllegalStateException("State is not READY");
        int itemId = canonicalizeItemId(initialItemId, itemManager, client);
        if (AUTO_UNLOCKED_ITEMS.contains(itemId)) return true;
        Map<Integer, UnlockedItem> map = unlockedItemsDataProvider.getUnlockedItemsMap();
        if (map == null) throw new IllegalStateException("Unlocked items map is null");
        return map.containsKey(itemId);
    }

    public CompletableFuture<Void> removeUnlockedItemById(int itemId) {
        try {
            if (!hasUnlockedItem(itemId)) {
                log.warn("Attempted to remove unlocked item with id {} but it is not unlocked yet", itemId);
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception ex) { return CompletableFuture.failedFuture(ex); }
        return unlockedItemsDataProvider.removeUnlockedItemById(itemId)
            .thenRun(() -> log.info("Removed unlocked item with id {}", itemId));
    }

    private boolean unlockedItemsDataProviderNotReady() {
        return unlockedItemsDataProvider.getState().get() != UnlockedItemsDataProvider.State.Ready;
    }

    private void currentAccountConfigurationChangeListener(AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) return;
        checkAndNotifyNonSupportedWorldType();
    }

    private void checkAndNotifyNonSupportedWorldType() {
        boolean isSupported;
        try { isSupported = isCurrentWorldSupportedForUnlockingItems(); } catch (Exception e) { return; }
        if (isSupported || hasNotifiedPlayerOfNonSupportedWorldType) return;
        hasNotifiedPlayerOfNonSupportedWorldType = true;
        buChatService.sendMessage(chatMessageProvider.messageFor(MessageKey.ITEM_UNLOCKS_UNSUPPORTED_WORLD));
    }

    private CompletableFuture<Void> unlockItem(int initialItemId) {
        return unlockItem(initialItemId, null);
    }

    private CompletableFuture<Void> unlockItem(int initialItemId, Integer droppedByNPCId) {
        if (initialItemId <= 1)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Item id must be greater than 1"));
        try {
            if (!isCurrentWorldSupportedForUnlockingItems()) {
                log.info("Current world is not supported for unlocking items");
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception ex) { return CompletableFuture.failedFuture(ex); }
        if (minigameService.isPlayingLastManStanding()) return CompletableFuture.completedFuture(null);

        ItemComposition initialComp = client.getItemDefinition(initialItemId);
        if (initialComp.getPlaceholderTemplateId() != -1) return CompletableFuture.completedFuture(null);

        int itemId;
        try {
            itemId = canonicalizeItemId(initialItemId, itemManager, client);
            if (hasUnlockedItem(itemId)) return CompletableFuture.completedFuture(null);
        } catch (Exception ex) { return CompletableFuture.failedFuture(ex); }

        ItemComposition itemComp = client.getItemDefinition(itemId);
        final boolean isTradeable = itemComp.isTradeable();
        final String itemName = itemComp.getName();
        final int fItemId = itemId;
        final long acquiredByAccountHash = client.getAccountHash();

        return gameRulesService.waitUntilGameRulesReady(null).thenCompose(__ -> {
            GameRules gameRules = gameRulesService.getGameRules().get();
            log.debug("is only for traded items: {} - is tradeable: {}", gameRules.isOnlyForTradeableItems(), isTradeable);
            if (gameRules.isOnlyForTradeableItems() && !isTradeable)
                return CompletableFuture.completedFuture(null);
            UnlockedItem unlockedItem = new UnlockedItem(
                fItemId, itemName, acquiredByAccountHash,
                new ISOOffsetDateTime(OffsetDateTime.now()), droppedByNPCId);
            log.info("Unlocked item ({}) '{}'", fItemId, itemName);
            return unlockedItemsDataProvider.addUnlockedItem(unlockedItem);
        });
    }

    private boolean isCurrentWorldSupportedForUnlockingItems() throws Exception {
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult == null) throw new Exception("Failed to get worlds");
        World world = worldResult.findWorld(client.getWorld());
        if (world == null) throw new Exception("Failed to find world with id " + client.getWorld());
        EnumSet<WorldType> worldTypes = world.getTypes();
        return worldTypes.isEmpty() || worldTypes.stream().allMatch(supportedWorldTypes::contains);
    }

    private void unlockedItemDataProviderStateListener(AbstractDataProvider.State state) {
        if (state != AbstractDataProvider.State.Ready) return;
        clientThread.invokeLater(() -> {
            Map<Integer, UnlockedItem> map = unlockedItemsDataProvider.getUnlockedItemsMap();
            if (map == null) throw new IllegalStateException("Unlocked items map is null");
            buChatService.sendMessage(String.format("Loaded with %d unlocked items.", map.size()));
            log.debug("Unlocked items data provider ready for item unlock service first time, checking inventory");
            INCLUDED_CONTAINER_IDS.stream().map(client::getItemContainer).forEach(this::unlockItemsFromItemContainer);
        });
    }

    private void unlockItemsFromItemContainer(ItemContainer itemContainer) {
        if (unlockedItemsDataProviderNotReady() || itemContainer == null) return;
        Arrays.stream(itemContainer.getItems())
            .filter(Objects::nonNull)
            .filter(item -> item.getQuantity() > 0)
            .map(Item::getId)
            .filter(id -> !hasUnlockedItem(id))
            .map(this::unlockItem)
            .forEach(addErrorLogging("Failed to unlock item in item container changed"));
    }

    /**
     * Clears all unlocked items from storage, then re-unlocks any items
     * currently present in tracked containers (bank, inventory, rewards, etc.).
     */
    public CompletableFuture<Void> resetUnlockedItemsFromCurrentState() {
        if (unlockedItemsDataProvider.getState().get() != UnlockedItemsDataProvider.State.Ready) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unlocked items are not ready"));
        }

        Map<Integer, UnlockedItem> current = unlockedItemsDataProvider.getUnlockedItemsMap();
        CompletableFuture<Void> clearFuture;
        if (current == null || current.isEmpty()) {
            clearFuture = CompletableFuture.completedFuture(null);
        } else {
            List<CompletableFuture<Void>> deletes = new ArrayList<>();
            suppressDeleteNotifications = true;
            for (Integer id : current.keySet()) {
                deletes.add(unlockedItemsDataProvider.removeUnlockedItemById(id));
            }
            clearFuture = CompletableFuture
                .allOf(deletes.toArray(new CompletableFuture[0]))
                .whenComplete((__, __e) -> suppressDeleteNotifications = false);
        }

        return clearFuture.thenCompose(__ -> {
            CompletableFuture<Void> rescan = new CompletableFuture<>();
            clientThread.invokeLater(() -> {
                ignoreHasUnlockedDuringReset = true;
                try {
                    INCLUDED_CONTAINER_IDS.stream()
                        .map(client::getItemContainer)
                        .forEach(this::unlockItemsFromItemContainer);
                    rescan.complete(null);
                } catch (Exception ex) {
                    rescan.completeExceptionally(ex);
                } finally {
                    ignoreHasUnlockedDuringReset = false;
                }
            });
            return rescan;
        }).thenRun(() ->
            buChatService.sendMessage("All unlocked items have been cleared. "
                + "Items currently in your bank and inventory have been re-checked.")
        );
    }

    /** Clears any queued unlock popups without affecting stored unlock data. */
    public void clearQueuedUnlockPopups() {
        itemUnlockOverlay.clear();
    }
}
