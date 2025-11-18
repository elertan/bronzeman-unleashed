package com.elertan;

import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.overlays.ItemUnlockOverlay;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
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

    private static final Map<String, Integer> MAP_ITEM_NAMES = new HashMap<String, Integer>() {{
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
    private static final Set<WorldType> supportedWorldTypes = ImmutableSet.of(
        WorldType.MEMBERS,
        WorldType.PVP,
        WorldType.SKILL_TOTAL,
        WorldType.HIGH_RISK,
        WorldType.FRESH_START_WORLD,
        WorldType.LAST_MAN_STANDING
    );
    private final ConcurrentLinkedQueue<Consumer<UnlockedItem>> newUnlockedItemListeners = new ConcurrentLinkedQueue<>();
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private WorldService worldService;
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
    private final Consumer<UnlockedItemsDataProvider.State> unlockedItemDataProviderStateListener = this::unlockedItemDataProviderStateListener;
    private UnlockedItemsDataProvider.UnlockedItemsMapListener unlockedItemsMapListener;
    private volatile boolean hasNotifiedPlayerOfNonSupportedWorldType = false;
    private final Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;

    @Override
    public void startUp() throws Exception {
        unlockedItemsMapListener = new UnlockedItemsDataProvider.UnlockedItemsMapListener() {

            @Override
            public void onUpdate(UnlockedItem unlockedItem) {
                // We can consider these to be newly unlocked items

                itemUnlockOverlay.enqueueShowUnlock(
                    unlockedItem.getId(),
                    unlockedItem.getAcquiredByAccountHash(),
                    unlockedItem.getDroppedByNPCId()
                );

                if (buPluginConfig.showItemUnlocksInChat()) {
                    CompletableFuture<String> itemIconTagFuture;
                    if (buPluginConfig.useItemIconsInChat()) {
                        itemIconTagFuture = buChatService.getItemIconTag(
                            unlockedItem.getId());
                    } else {
                        itemIconTagFuture = CompletableFuture.completedFuture(null);
                    }

                    itemIconTagFuture.whenComplete((itemIconTag, throwable) -> {
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

                for (Consumer<UnlockedItem> listener : newUnlockedItemListeners) {
                    try {
                        listener.accept(unlockedItem);
                    } catch (Exception ex) {
                        log.error("unlockedItemListener: onUpdate", ex);
                    }
                }
            }

            @Override
            public void onDelete(UnlockedItem unlockedItem) {
                // We can consider this re-locking items

                CompletableFuture<String> itemIconTagFuture;
                if (buPluginConfig.useItemIconsInChat()) {
                    itemIconTagFuture = buChatService.getItemIconTag(unlockedItem.getId());
                } else {
                    itemIconTagFuture = CompletableFuture.completedFuture(null);
                }

                itemIconTagFuture.whenComplete((itemIconTag, throwable) -> {
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
        unlockedItemsDataProvider.addStateListener(unlockedItemDataProviderStateListener);
        accountConfigurationService.addCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);
        unlockedItemsDataProvider.removeStateListener(unlockedItemDataProviderStateListener);
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
        if (unlockedItemsDataProvider.getState() != UnlockedItemsDataProvider.State.Ready) {
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
        if (unlockedItemsDataProvider.getState() != UnlockedItemsDataProvider.State.Ready) {
            return;
        }

        Collection<ItemStack> itemStack = event.getItems();
        NPCComposition npcComposition = event.getComposition();
        List<Integer> itemIds = itemStack.stream()
            .map(ItemStack::getId)
            .collect(Collectors.toList());

        for (int itemId : itemIds) {
            if (hasUnlockedItem(itemId)) {
                continue;
            }

            unlockItem(itemId, npcComposition.getId()).whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to unlock item in on server npc loot", throwable);
                }
            });
        }
    }

    public void addNewUnlockedItemListener(Consumer<UnlockedItem> consumer) {
        newUnlockedItemListeners.add(consumer);
    }

    public void removeNewUnlockedItemListener(Consumer<UnlockedItem> consumer) {
        newUnlockedItemListeners.remove(consumer);
    }

    public boolean hasUnlockedItem(int itemId) throws IllegalStateException {
        if (unlockedItemsDataProvider.getState() != UnlockedItemsDataProvider.State.Ready) {
            throw new IllegalStateException("State is not READY");
        }

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
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean hasUnlockedItem;
        try {
            hasUnlockedItem = hasUnlockedItem(itemId);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
            return future;
        }
        if (!hasUnlockedItem) {
            log.warn(
                "Attempted to remove unlocked item with id {} but it is not unlocked yet",
                itemId
            );
            future.complete(null);
            return future;
        }

        unlockedItemsDataProvider.removeUnlockedItemById(itemId).whenComplete((__, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            log.info("Removed unlocked item with id {}", itemId);
            future.complete(null);
        });

        return future;
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            return;
        }
        checkAndNotifyNonSupportedWorldType();
    }


    private void checkAndNotifyNonSupportedWorldType() {
        boolean isSupported;
        try {
            isSupported = isCurrentWorldSupportedForUnlockingItems();
        } catch (Exception e) {
            return;
        }

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

    private CompletableFuture<Void> unlockItem(int initialItemId, Integer droppedByNPCId) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (initialItemId <= 1) {
            Exception ex = new IllegalArgumentException("Item id must be greater than 1");
            future.completeExceptionally(ex);
            return future;
        }

        // We don't support all world types, for example we don't want unlocks on seasonal modes
        try {
            if (!isCurrentWorldSupportedForUnlockingItems()) {
                log.info("Current world is not supported for unlocking items");
                future.complete(null);
                return future;
            }
        } catch (Exception ex) {
            future.completeExceptionally(ex);
            return future;
        }

        // Disable LMS unlocks
        if (minigameService.isPlayingLastManStanding()) {
            future.complete(null);
            return future;
        }

        // We want the base item, not a noted item or similar
        int itemId = itemManager.canonicalize(initialItemId);

        // If necessary, we also need to map the item to a different one
        // for example ensouled heads have multiple variations of the same item
        // one that you can re-animate, and one you cannot.
        // We don't want to unlock these multiple times
        if (ITEM_MAPPING_ITEM_IDS.contains(itemId)) {
            Collection<ItemMapping> mapping = ItemMapping.map(itemId);
            if (mapping == null || mapping.isEmpty()) {
                Exception ex = new Exception("Failed to map item id " + itemId);
                future.completeExceptionally(ex);
                return future;
            }
            final Optional<ItemMapping> optMap = mapping.stream().findFirst();
            final ItemMapping map = optMap.orElse(null);
            itemId = map.getTradeableItem();
        }

        // If necessary, we also need to map the item to a different one by name
        // for example clue scrolls have like 50 variations, but they're
        // essentially the same item
        ItemComposition itemComposition = itemManager.getItemComposition(itemId);
        Integer nameMappedItemID = MAP_ITEM_NAMES.get(itemComposition.getName());
        if (nameMappedItemID != null) {
            itemId = nameMappedItemID;
            itemComposition = itemManager.getItemComposition(itemId);
        }

        try {
            if (hasUnlockedItem(itemId)) {
//                log.info("Item with id {} is already unlocked", itemId);
                future.complete(null);
                return future;
            }
        } catch (Exception ex) {
            future.completeExceptionally(ex);
            return future;
        }

        final boolean fIsTradeable = itemComposition.isTradeable();
        final String fItemName = itemComposition.getName();
        final int fItemId = itemId;
        gameRulesService
            .waitUntilGameRulesReady(null)
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                    return;
                }

                GameRules gameRules = gameRulesService.getGameRules();
                log.debug(
                    "is only for traded items: {} - is tradedable: {}",
                    gameRules.isOnlyForTradeableItems(),
                    fIsTradeable
                );
                if (gameRules.isOnlyForTradeableItems() && !fIsTradeable) {
                    future.complete(null);
                    return;
                }

                long acquiredByAccountHash = client.getAccountHash();
                ISOOffsetDateTime acquiredAt = new ISOOffsetDateTime(OffsetDateTime.now());

                UnlockedItem unlockedItem = new UnlockedItem(
                    fItemId,
                    fItemName,
                    acquiredByAccountHash,
                    acquiredAt,
                    droppedByNPCId
                );
                log.info("Unlocked item ({}) '{}'", fItemId, fItemName);
                unlockedItemsDataProvider.addUnlockedItem(unlockedItem)
                    .whenComplete((__2, throwable2) -> {
                        if (throwable2 != null) {
                            future.completeExceptionally(throwable2);
                            return;
                        }

                        future.complete(null);
                    });

            });
        return future;
    }

    private boolean isCurrentWorldSupportedForUnlockingItems() throws Exception {
        int worldNumber = client.getWorld();
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult == null) {
            throw new Exception("Failed to get worlds");
        }
        World world = worldResult.findWorld(worldNumber);
        if (world == null) {
            throw new Exception("Failed to find world with id " + worldNumber);
        }
        EnumSet<WorldType> worldTypes = world.getTypes();
        boolean hasUnsupportedWorldType = !worldTypes.isEmpty() && worldTypes.stream()
            .anyMatch(t -> !supportedWorldTypes.contains(
                t));

        return !hasUnsupportedWorldType;
    }

    private void unlockedItemDataProviderStateListener(UnlockedItemsDataProvider.State state) {
        if (state != UnlockedItemsDataProvider.State.Ready) {
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
            for (Integer containerId : INCLUDED_CONTAINER_IDS) {
                ItemContainer itemContainer = client.getItemContainer(containerId);
                unlockItemsFromItemContainer(itemContainer);
            }
        });
    }

    private void unlockItemsFromItemContainer(ItemContainer itemContainer) {
        if (unlockedItemsDataProvider.getState() != UnlockedItemsDataProvider.State.Ready) {
            return;
        }

        if (itemContainer == null) {
            return;
        }

        for (Item item : itemContainer.getItems()) {
            if (item == null) {
                continue;
            }
            int itemId = item.getId();

            if (item.getQuantity() <= 0) {
                continue;
            }

            if (hasUnlockedItem(itemId)) {
                continue;
            }

            unlockItem(itemId, null).whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.info("Failed to unlock item in item container changed", throwable);
                }
            });
        }
    }
}
