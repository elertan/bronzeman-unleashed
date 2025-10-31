package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUSoundHelper;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.data.GroundItemOwnedByDataProvider;
import com.elertan.models.AccountHash;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByKey;
import com.google.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private BUChatService buChatService;
    @Inject
    private GroundItemOwnedByDataProvider groundItemOwnedByDataProvider;

    private GroundItemOwnedByDataProvider.Listener groundItemOwnedByDataProviderListener;

    @Inject
    public GroundItemsPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {
        groundItemOwnedByDataProviderListener = new GroundItemOwnedByDataProvider.Listener() {
            @Override
            public void onReadAll(Map<GroundItemOwnedByKey, AccountHash> map) {

            }

            @Override
            public void onUpdate(GroundItemOwnedByKey key, AccountHash value) {

            }

            @Override
            public void onDelete(GroundItemOwnedByKey key) {

            }
        };
        groundItemOwnedByDataProvider.addListener(groundItemOwnedByDataProviderListener);
    }

    @Override
    public void shutDown() throws Exception {
        groundItemOwnedByDataProvider.removeListener(groundItemOwnedByDataProviderListener);
    }

    public void onItemSpawned(ItemSpawned event) {
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

        ConcurrentHashMap<GroundItemOwnedByKey, AccountHash> groundItemOwnedByMap = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            log.warn("Ground item spawned for me but groundItemOwnedByMap is null");
            return;
        }

        AccountHash accountHash = groundItemOwnedByMap.get(key);
        if (accountHash != null && accountHash.getValue() == client.getAccountHash()) {
            log.info("gi {} already in groundItemOwnedByMap for me, ignore", key);
            return;
        }
        AccountHash newAccountHash = new AccountHash(client.getAccountHash());

        groundItemOwnedByDataProvider.update(key, newAccountHash)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider update failed", throwable);
                }
            });
    }

    public void onItemDespawned(ItemDespawned event) {
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

        ConcurrentHashMap<GroundItemOwnedByKey, AccountHash> groundItemOwnedByMap = groundItemOwnedByDataProvider.getGroundItemOwnedByMap();
        if (groundItemOwnedByMap == null) {
            log.warn("Ground item despawned for me but groundItemOwnedByMap is null");
            return;
        }

        AccountHash accountHash = groundItemOwnedByMap.get(key);
        if (accountHash == null) {
            log.info("gi {} already deleted, ignore", key);
            return;
        }

        groundItemOwnedByDataProvider.delete(key)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("GroundItemOwnedByDataProvider delete failed", throwable);
                }
            });
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        PolicyContext context = createContext();
        if (context.isMustEnforceStrictPolicies()) {
            enforceItemTakePolicy(event);
            return;
        }
        GameRules gameRules = context.getGameRules();
        if (gameRules == null || !gameRules.isRestrictGroundItems()) {
            return;
        }
        enforceItemTakePolicy(event);
    }

    private void enforceItemTakePolicy(MenuOptionClicked event) {
        MenuAction menuAction = event.getMenuAction();
        boolean isGroundItemMenuAction =
            menuAction.ordinal() >= MenuAction.GROUND_ITEM_FIRST_OPTION.ordinal()
                && menuAction.ordinal() <= MenuAction.GROUND_ITEM_FIFTH_OPTION.ordinal();
        if (!isGroundItemMenuAction) {
            return;
        }

        String menuOption = event.getMenuOption();
        if (!menuOption.equals("Take")) {
            return;
        }
        int itemId = event.getId();
        if (itemId <= 1) {
            return;
        }

        TileItem tileItem = getClickedTileItem(event);
        if (tileItem == null) {
            log.warn(
                "Ground item not found at scene ({}, {}) for id {}",
                event.getParam0(),
                event.getParam1(),
                itemId
            );
            return;
        }

        ItemComposition itemComposition = client.getItemDefinition(itemId);

        int ownership = tileItem.getOwnership();
        if (ownership == TileItem.OWNERSHIP_NONE) {
            log.info("Item '{}' is not owned by anyone, allow take", itemComposition.getName());
            return;
        }
        if (ownership == TileItem.OWNERSHIP_SELF) {
            log.info("Item '{}' is owned by me, allow take", itemComposition.getName());
            return;
        }

        event.consume();
//        buSoundHelper.playDisabledSound();

        buChatService.sendMessage(
            "You're a Bronzeman with ground item restrictions, so you can't take that.");

//        log.info(
//            "Taking '{}' x{} at scene ({}, {}) plane {}",
//            itemComposition.getName(),
//            tileItem.getQuantity(),
//            event.getParam0(),
//            event.getParam1(),
//            client.getPlane()
//        );
    }

    private TileItem getClickedTileItem(MenuOptionClicked event) {
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
                return ti;
            }
        }
        return null;
    }
}
