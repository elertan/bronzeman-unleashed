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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

@Slf4j
@Singleton
public class ShopPolicy extends PolicyBase implements BUPluginLifecycle {
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

    @Inject
    private OverlayManager overlayManager;

    private final ShopOverlay shopOverlay = new ShopOverlay();

    private final GameRulesService gameRulesService;

    private boolean isShopOpen = false;

    @Inject
    public ShopPolicy(AccountConfigurationService accountConfigurationService, GameRulesService gameRulesService) {
        super(accountConfigurationService, gameRulesService);

        this.gameRulesService = gameRulesService;
    }

    @Override
    public void startUp() throws Exception {
        overlayManager.add(shopOverlay);
    }

    @Override
    public void shutDown() throws Exception {
        overlayManager.remove(shopOverlay);
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
    }

    private void onShopClosed() {
        isShopOpen = false;
    }

    /**
     * Draws unlocked-item checkmarks over shop items without using sprite IDs or widget children.
     * Placement, size, and opacity match the previous widget-based approach.
     */
    private class ShopOverlay extends Overlay {
        private static final int CHECKMARK_SIZE = 8;
        private BufferedImage checkmarkImg;

        private ShopOverlay() {
            setPosition(OverlayPosition.DYNAMIC);
            setLayer(OverlayLayer.ABOVE_WIDGETS);
            setPriority(0);
        }

        @Override
        public java.awt.Dimension render(Graphics2D g) {
            // Gate by policy, shop visibility, and user config
            if (!isShopOpen) {
                return null;
            }
            if (!shouldEnforcePolicies()) {
                return null;
            }
            if (!buPluginConfig.showUnlockedItemsIndicatorInShops()) {
                return null;
            }

            // Resolve the items container; if absent, nothing to draw
            Widget itemsContainer = client.getWidget(InterfaceID.Shopmain.ITEMS);
            if (itemsContainer == null) {
                return null;
            }
            Widget[] children = itemsContainer.getDynamicChildren();
            if (children == null || children.length == 0) {
                return null;
            }

            // Lazy-load the checkmark image
            if (checkmarkImg == null) {
                try {
                    checkmarkImg = buResourceService.getCheckmarkIconBufferedImage();
                } catch (Exception ignored) {
                    return null;
                }
            }

            // Clip drawings to the scrollable viewport so icons don't bleed while scrolling
            Shape oldClip = g.getClip();
            Rectangle viewport = itemsContainer.getBounds();
            g.setClip(viewport);

            // Draw semi-transparent checkmarks in the same bottom-right position as before
            Composite prev = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)); // opacity 50%

            for (Widget w : children) {
                if (w == null || w.isHidden()) {
                    continue;
                }
                int itemId = w.getItemId();
                if (itemId <= 0) {
                    continue;
                }

                boolean unlocked;
                try {
                    unlocked = itemUnlockService.hasUnlockedItem(itemId);
                } catch (Exception e) {
                    continue;
                }
                if (!unlocked) {
                    continue;
                }

                // Use absolute on-canvas bounds which already account for scroll
                Rectangle b = w.getBounds();
                int x = b.x + b.width - CHECKMARK_SIZE - 1;
                int y = b.y + b.height - CHECKMARK_SIZE - 1;

                g.drawImage(checkmarkImg, x, y, CHECKMARK_SIZE, CHECKMARK_SIZE, null);
            }

            // Restore state
            g.setComposite(prev);
            g.setClip(oldClip);

            return null;
        }
    }
}
