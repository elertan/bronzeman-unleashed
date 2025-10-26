package com.elertan.policies;

import com.elertan.*;
import com.elertan.models.UnlockedItem;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class ShopPolicy extends PolicyBase implements BUPluginLifecycle {
    private final static String OVERLAY_CHECKMARK_NAME = "overlayCheckmark";

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private BUResourceService buResourceService;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private ItemUnlockService itemUnlockService;

    private final GameRulesService gameRulesService;

    private boolean isShopOpen = false;

    @Inject
    public ShopPolicy(AccountConfigurationService accountConfigurationService, GameRulesService gameRulesService) {
        super(accountConfigurationService, gameRulesService);

        this.gameRulesService = gameRulesService;
    }

    @Override
    public void startUp() throws Exception {
        itemUnlockService.addNewUnlockedItemListener(this::newUnlockedItemListener);
        itemUnlockService.addLockUnlockedItemListener(this::lockUnlockedItemListener);
    }

    @Override
    public void shutDown() throws Exception {
        itemUnlockService.removeLockUnlockedItemListener(this::lockUnlockedItemListener);
        itemUnlockService.removeNewUnlockedItemListener(this::newUnlockedItemListener);
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        int groupId = event.getGroupId();
        if (groupId != WidgetID.SHOP_GROUP_ID) {
            return;
        }
        onShopOpened();
    }

    public void onWidgetClosed(WidgetClosed event) {
        int groupId = event.getGroupId();
        if (groupId != WidgetID.SHOP_GROUP_ID) {
            return;
        }
        onShopClosed();
    }

    private void onShopOpened() {
        isShopOpen = true;

        clientThread.invokeLater(this::manageUnlockIndicatorInShop);
    }

    private void onShopClosed() {
        isShopOpen = false;
    }

    private void newUnlockedItemListener(UnlockedItem unlockedItem) {
        clientThread.invokeLater(this::manageUnlockIndicatorInShop);
    }

    private void lockUnlockedItemListener(Integer id) {
        clientThread.invokeLater(this::manageUnlockIndicatorInShop);
    }

    private void manageUnlockIndicatorInShop() {
        if (!isShopOpen) {
            return;
        }
        if (!shouldEnforcePolicies()) {
            return;
        }

        List<Widget> shopItemWidgets = getShopItemWidgets();
        if (shopItemWidgets == null) {
            return;
        }

        Widget itemsContainer = client.getWidget(InterfaceID.Shopmain.ITEMS);
        if (itemsContainer == null) {
            log.info("Shop main items widget not found");
            return;
        }

        // Hide previous overlays (on updates)
        Widget[] children = itemsContainer.getDynamicChildren();
        if (children != null) {
            for (Widget w : children) {
                String n = w.getName();
                if (n != null && n.equals(OVERLAY_CHECKMARK_NAME)) {
                    w.setHidden(true);
                }
            }
        }

        if (!buPluginConfig.showUnlockedItemsIndicatorInShops()) {
            return;
        }

        for (Widget shopItemWidget : shopItemWidgets) {
            int itemId = shopItemWidget.getItemId();

            boolean hasUnlockedItem;
            try {
                hasUnlockedItem = itemUnlockService.hasUnlockedItem(itemId);
            } catch (Exception e) {
                log.error("Failed to check hasUnlockedItem({}) in onShopOpened", itemId, e);
                continue;
            }

            if (!hasUnlockedItem) {
                continue;
            }

            // Checkmark
            Widget checkmarkOverlay = itemsContainer.createChild(-1, WidgetType.GRAPHIC);
            int checkmarkSize = 8;

            checkmarkOverlay.setSpriteId(buResourceService.getBuSprites().getCheckmarkId());
            checkmarkOverlay.setOpacity(50);
            checkmarkOverlay.setOriginalWidth(checkmarkSize);
            checkmarkOverlay.setOriginalHeight(checkmarkSize);
            checkmarkOverlay.setOriginalX(shopItemWidget.getRelativeX() + shopItemWidget.getWidth() - checkmarkSize - 1);
            checkmarkOverlay.setOriginalY(shopItemWidget.getRelativeY() + shopItemWidget.getHeight() - checkmarkSize - 1);
            checkmarkOverlay.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            checkmarkOverlay.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            checkmarkOverlay.setHidden(false);
            checkmarkOverlay.setName(OVERLAY_CHECKMARK_NAME);

            checkmarkOverlay.revalidate();
        }

        itemsContainer.revalidate();

    }

    private List<Widget> getShopItemWidgets() {
        Widget root = client.getWidget(InterfaceID.Shopmain.ITEMS);
        if (root == null) {
            log.error("Shop root widget not found");
            return null;
        }

        Widget[] children = root.getDynamicChildren();
        if (children == null || children.length == 0) {
            log.error("Shop root widget has no children");
            return null;
        }

        return Arrays.stream(children).filter(c -> c.getItemId() > 0).collect(Collectors.toList());
    }
}
