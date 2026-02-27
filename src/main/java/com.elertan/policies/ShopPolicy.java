package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginConfig;
import com.elertan.BUResourceService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.PolicyService;
import com.elertan.BUChatService;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.models.GameRules;
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
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
@Singleton
public class ShopPolicy extends PolicyBase {

    private final ShopmainOverlay shopmainOverlay = new ShopmainOverlay();
    @Inject private Client client;
    @Inject private BUResourceService buResourceService;
    @Inject private BUPluginConfig buPluginConfig;
    @Inject private ItemUnlockService itemUnlockService;
    @Inject private OverlayManager overlayManager;
    @Inject private BUChatService buChatService;

    @Inject
    public ShopPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (accountConfigurationService.isBronzemanEnabled()
            && event.getGroupId() == InterfaceID.SHOPMAIN) {
            overlayManager.add(shopmainOverlay);
        }
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (accountConfigurationService.isBronzemanEnabled()
            && event.getGroupId() == InterfaceID.SHOPMAIN) {
            overlayManager.remove(shopmainOverlay);
        }
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;

        // Reuse the same rule as GE locked-item prevention
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventGrandExchangeBuyOffers)) return;

        MenuAction action = event.getMenuAction();
        String option = event.getMenuOption();
        if (action != MenuAction.CC_OP && action != MenuAction.CC_OP_LOW_PRIORITY) return;
        if (option == null || !option.startsWith("Buy")) return;
        if (event.getId() <= 0) return;

        try {
            boolean unlocked = itemUnlockService.hasUnlockedItem(event.getId());
            if (!unlocked) {
                event.consume();
                buChatService.sendRestrictionMessage(MessageKey.SHOP_BUY_RESTRICTION);
            }
        } catch (Exception e) {
            // If the unlock check fails, allow the buy rather than hard-blocking
        }
    }

    private class ShopmainOverlay extends Overlay {
        private static final int CHECKMARK_SIZE = 8;

        private ShopmainOverlay() {
            setPosition(OverlayPosition.DYNAMIC);
            setLayer(OverlayLayer.ABOVE_WIDGETS);
        }

        @Override
        public Dimension render(Graphics2D g) {
            if (!buPluginConfig.showUnlockedItemsIndicatorInShops()) return null;
            Widget itemsContainer = client.getWidget(InterfaceID.Shopmain.ITEMS);
            if (itemsContainer == null) return null;
            Widget[] children = itemsContainer.getDynamicChildren();
            if (children == null || children.length == 0) return null;

            BufferedImage checkmark = buResourceService.getCheckmarkIconBufferedImage();
            Shape oldClip = g.getClip();
            g.setClip(itemsContainer.getBounds());
            Composite prev = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

            for (Widget w : children) {
                if (w == null || w.isHidden() || w.getItemId() <= 0) continue;
                boolean unlocked;
                try { unlocked = itemUnlockService.hasUnlockedItem(w.getItemId()); }
                catch (Exception e) { continue; }
                if (!unlocked) continue;
                Rectangle b = w.getBounds();
                g.drawImage(checkmark, b.x + b.width - CHECKMARK_SIZE - 1,
                    b.y + b.height - CHECKMARK_SIZE - 1, CHECKMARK_SIZE, CHECKMARK_SIZE, null);
            }

            g.setComposite(prev);
            g.setClip(oldClip);
            return null;
        }
    }
}
