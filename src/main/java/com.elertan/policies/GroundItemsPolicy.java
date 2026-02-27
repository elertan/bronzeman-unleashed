package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
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
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private BUChatService buChatService;
    @Inject private ChatMessageProvider chatMessageProvider;
    @Inject private BUPluginConfig buPluginConfig;
    @Inject private GroundItemOwnedByDataProvider groundItemOwnedByDataProvider;
    @Inject private MemberService memberService;
    @Inject private MinigameService minigameService;
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
            @Override public void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map) {}
            @Override public void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value) {}
            @Override public void onRemove(GroundItemOwnedByKey key, String entryKey) {}
        };
        groundItemOwnedByDataProvider.addMapListener(groundItemOwnedByDataProviderListener);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            () -> clientThread.invoke(() -> cleanupExpiredGroundItems(true)),
            10, 10, TimeUnit.SECONDS);
        groundItemOwnedByDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider await failed", throwable);
                return;
            }
            cleanupExpiredGroundItems(false);
        });
    }

    @Override
    public void shutDown() throws Exception {
        groundItemOwnedByDataProvider.removeMapListener(groundItemOwnedByDataProviderListener);
        scheduler.shutdownNow();
    }

    private boolean isOwnedByMeOrGroup(TileItem tileItem) {
        int o = tileItem.getOwnership();
        return o == TileItem.OWNERSHIP_SELF || o == TileItem.OWNERSHIP_GROUP;
    }

    private GroundItemOwnedByKey keyFor(int itemId, Tile tile) {
        WorldPoint wp = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        WorldView wv = client.findWorldViewFromWorldPoint(wp);
        return GroundItemOwnedByKey.of(itemId, client.getWorld(), wv.getId(), wp);
    }

    public void onItemSpawned(ItemSpawned event) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isRestrictGroundItems)) return;
        TileItem tileItem = event.getItem();
        if (!isOwnedByMeOrGroup(tileItem)) return;
        GroundItemOwnedByKey key = keyFor(tileItem.getId(), event.getTile());
        if (groundItemOwnedByDataProvider.getGroundItemOwnedByMap() == null) {
            log.warn("Ground item spawned for me but groundItemOwnedByMap is null");
            return;
        }
        long despawnTicks = tileItem.getDespawnTime() - client.getTickCount();
        GroundItemOwnedByData data = new GroundItemOwnedByData(
            client.getAccountHash(),
            new ISOOffsetDateTime(OffsetDateTime.now().plus(TickUtils.ticksToDuration(despawnTicks))),
            null);
        groundItemOwnedByDataProvider.addEntry(key, data).whenComplete((r, t) -> {
            if (t != null) log.error("GroundItemOwnedByDataProvider addEntry failed", t);
        });
    }

    public void onItemDespawned(ItemDespawned event) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        TileItem tileItem = event.getItem();
        if (!isOwnedByMeOrGroup(tileItem)) return;
        GroundItemOwnedByKey key = keyFor(tileItem.getId(), event.getTile());
        if (!groundItemOwnedByDataProvider.hasEntries(key)) {
            log.debug("gi {} has no entries, ignore", key);
            return;
        }
        groundItemOwnedByDataProvider.removeOneEntry(key).whenComplete((r, t) -> {
            if (t != null) log.error("GroundItemOwnedByDataProvider removeOneEntry failed", t);
        });
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        enforceItemTakePolicyWhereNecessary(event, createContext());
    }

    private void enforceItemTakePolicyWhereNecessary(MenuOptionClicked event, PolicyContext context) {
        MenuAction menuAction = event.getMenuAction();
        boolean isGroundItem = menuAction.ordinal() >= MenuAction.GROUND_ITEM_FIRST_OPTION.ordinal()
            && menuAction.ordinal() <= MenuAction.GROUND_ITEM_FIFTH_OPTION.ordinal();
        boolean isWidgetTarget = menuAction.ordinal() == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM.ordinal();
        if (!isGroundItem && !isWidgetTarget) return;
        String menuOption = event.getMenuOption();
        if (!menuOption.equals("Take") && !menuOption.equals("Cast")) return;
        int itemId = event.getId();
        if (itemId <= 1 || minigameService.isPlayingLastManStanding()) return;

        GetClickedTileItemOutput output = getClickedTileItem(event);
        if (output == null) {
            log.warn("Ground item not found at scene ({}, {}) for id {}",
                event.getParam0(), event.getParam1(), itemId);
            return;
        }
        int ownership = output.getTileItem().getOwnership();
        if (ownership == TileItem.OWNERSHIP_NONE) return;

        GroundItemOwnedByKey key = keyFor(itemId, output.getTile());
        if (groundItemOwnedByDataProvider.getGroundItemOwnedByMap() == null) {
            if (context.shouldApplyForRules(
                r -> r.isRestrictGroundItems() || r.isRestrictPlayerVersusPlayerLoot())) {
                handleRestrictedClick(event);
                buChatService.sendErrorMessage(
                    chatMessageProvider.messageFor(MessageKey.STILL_LOADING_PLEASE_WAIT));
            }
            return;
        }

        if (groundItemOwnedByDataProvider.hasEntries(key)) {
            if (context.shouldApplyForRules(GameRules::isRestrictPlayerVersusPlayerLoot)
                && isPvpLootFromNonGroupMember(key, itemId)) {
                buChatService.sendRestrictionMessage(
                    MessageKey.PLAYER_VERSUS_PLAYER_LOOT_RESTRICTION);
            }
            return;
        }
        if (ownership == TileItem.OWNERSHIP_SELF) return;

        if (context.shouldApplyForRules(GameRules::isRestrictGroundItems)) {
            handleRestrictedClick(event);
            buChatService.sendRestrictionMessage(isWidgetTarget
                ? MessageKey.GROUND_ITEM_CAST_RESTRICTION
                : MessageKey.GROUND_ITEM_TAKE_RESTRICTION);
        }
    }

    private boolean isRestrictedGroundMenuEntry(MenuEntry entry, PolicyContext context) {
        final int sceneX = entry.getParam0(), sceneY = entry.getParam1(), itemId = entry.getIdentifier();
        if (itemId <= 1 || minigameService.isPlayingLastManStanding()) return false;

        GetClickedTileItemOutput output = getTileItemFromScene(sceneX, sceneY, itemId);
        if (output == null) {
            return false;
        }
        int ownership = output.getTileItem().getOwnership();
        if (ownership == TileItem.OWNERSHIP_NONE) return false;

        GroundItemOwnedByKey key = keyFor(itemId, output.getTile());
        if (groundItemOwnedByDataProvider.getGroundItemOwnedByMap() == null) {
            return context.shouldApplyForRules(
                r -> r.isRestrictGroundItems() || r.isRestrictPlayerVersusPlayerLoot());
        }

        if (groundItemOwnedByDataProvider.hasEntries(key)) {
            return context.shouldApplyForRules(GameRules::isRestrictPlayerVersusPlayerLoot)
                && isPvpLootFromNonGroupMember(key, itemId);
        }

        if (ownership == TileItem.OWNERSHIP_SELF) return false;
        return context.shouldApplyForRules(GameRules::isRestrictGroundItems);
    }

    private void handleRestrictedClick(MenuOptionClicked event) {
        event.consume();
    }

    private boolean isPvpLootFromNonGroupMember(GroundItemOwnedByKey key, int itemId) {
        ConcurrentHashMap<String, GroundItemOwnedByData> entries =
            groundItemOwnedByDataProvider.getEntries(key);
        if (entries == null) return false;
        for (GroundItemOwnedByData data : entries.values()) {
            String droppedBy = data.getDroppedByPlayerName();
            if (droppedBy == null) continue;
            log.info("PvP loot check for item '{}' dropped by {}", itemId, droppedBy);
            Member member = null;
            try { member = memberService.getMemberByName(droppedBy); } catch (Exception ignored) {}
            if (member != null) {
                log.info("Player '{}' is in our group, allow take", droppedBy);
                continue;
            }
            log.info("Player '{}' is not in our group, deny take", droppedBy);
            return true;
        }
        return false;
    }

    private GetClickedTileItemOutput getClickedTileItem(MenuOptionClicked event) {
        return getTileItemFromScene(event.getParam0(), event.getParam1(), event.getId());
    }

    private GetClickedTileItemOutput getTileItemFromScene(int sceneX, int sceneY, int itemId) {
        WorldView worldView = client.getTopLevelWorldView();
        final int plane = worldView.getPlane();
        Scene scene = worldView.getScene();
        if (scene == null) return null;
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null || plane < 0 || plane >= tiles.length) return null;
        if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length
            || sceneY >= tiles[plane][sceneX].length) return null;
        Tile tile = tiles[plane][sceneX][sceneY];
        if (tile == null || tile.getGroundItems() == null) return null;
        for (TileItem ti : tile.getGroundItems()) {
            if (ti.getId() == itemId) return new GetClickedTileItemOutput(tile, ti);
        }
        return null;
    }

    /**
     * @param currentAccountOnly when true, only removes entries belonging to the current account
     */
    private void cleanupExpiredGroundItems(boolean currentAccountOnly) {
        ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>>
            map = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (map == null || map.isEmpty()) return;
        OffsetDateTime now = OffsetDateTime.now();
        long accountHash = currentAccountOnly ? client.getAccountHash() : -1;
        for (Map.Entry<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> keyEntry : map.entrySet()) {
            ConcurrentHashMap<String, GroundItemOwnedByData> entries = keyEntry.getValue();
            if (entries == null) continue;
            for (Map.Entry<String, GroundItemOwnedByData> entry : entries.entrySet()) {
                GroundItemOwnedByData data = entry.getValue();
                if (currentAccountOnly && data.getAccountHash() != accountHash) continue;
                if (data.getDespawnsAt().getValue().isAfter(now)) continue;
                GroundItemOwnedByKey key = keyEntry.getKey();
                String entryKey = entry.getKey();
                log.debug("Cleaning up expired ground item {} entry {}", key, entryKey);
                groundItemOwnedByDataProvider.removeEntry(key, entryKey).whenComplete((r, t) -> {
                    if (t != null) log.error("Failed to clean up expired ground item {} entry {}", key, entryKey, t);
                });
            }
        }
    }

    @Value
    private static class GetClickedTileItemOutput {
        @NonNull Tile tile;
        @NonNull TileItem tileItem;
    }
}
