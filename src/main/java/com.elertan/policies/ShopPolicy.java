package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUResourceService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.PolicyService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
@Singleton
public class ShopPolicy extends PolicyBase implements BUPluginLifecycle {

    private final ShopmainOverlay shopmainOverlay = new ShopmainOverlay();
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


    @Inject
    public ShopPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {
    }

    @Override
    public void shutDown() throws Exception {
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        int groupId = event.getGroupId();
        if (groupId != InterfaceID.SHOPMAIN) {
            return;
        }
        onShopmainOpened();
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        int groupId = event.getGroupId();
        if (groupId != InterfaceID.SHOPMAIN) {
            return;
        }
        onShopmainClosed();
    }

    private void onShopmainOpened() {
        overlayManager.add(shopmainOverlay);
    }

    private void onShopmainClosed() {
        overlayManager.remove(shopmainOverlay);
    }

    /**
     * Draws unlocked-item checkmarks over shop items without using sprite IDs or widget children.
     * Placement, size, and opacity match the previous widget-based approach.
     */
    private class ShopmainOverlay extends Overlay {

        private static final int CHECKMARK_SIZE = 8;

        private ShopmainOverlay() {
            setPosition(OverlayPosition.DYNAMIC);
            setLayer(OverlayLayer.ABOVE_WIDGETS);
        }

        @Override
        public Dimension render(Graphics2D g) {
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

            BufferedImage checkmarkImg = buResourceService.getCheckmarkIconBufferedImage();

            // Clip drawings to the scrollable viewport so icons don't bleed while scrolling
            Shape oldClip = g.getClip();
            Rectangle viewport = itemsContainer.getBounds();
            g.setClip(viewport);

            // Draw semi-transparent checkmarks in the same bottom-right position as before
            Composite prev = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                0.5f
            )); // opacity 50%

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
