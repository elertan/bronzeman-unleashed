package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.PolicyService;
import com.elertan.models.GameRules;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

@Slf4j
@Singleton
public class GrandExchangePolicy extends PolicyBase {

    private final static int GE_SEARCH_BUILD_SCRIPT_ID = 751;
    @Inject
    private Client client;
    @Inject
    private ItemUnlockService itemUnlockService;

    private boolean geInterfaceOpen;

    @Inject
    public GrandExchangePolicy(
        AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService
    ) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void shutDown() throws Exception {
        geInterfaceOpen = false;
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            geInterfaceOpen = false;
        }
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.GE_OFFERS) {
            geInterfaceOpen = true;
        }
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.GE_OFFERS) {
            geInterfaceOpen = false;
        }
    }

    public void onScriptPostFired(ScriptPostFired event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        if (!geInterfaceOpen)
        {
            return;
        }

        int scriptId = event.getScriptId();
        if (scriptId == GE_SEARCH_BUILD_SCRIPT_ID) {
            onSearchBuild();
        }
    }

    private void onSearchBuild() {
        PolicyContext context = createContext();
        boolean preventLocked = context.shouldApplyForRules(GameRules::isPreventGrandExchangeBuyOffers);
        boolean preventGear = context.shouldApplyForRules(GameRules::isPreventGrandExchangeGearBuyOffers);
        if (!preventLocked && !preventGear) return;

        Widget searchResultsWidget = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
        if (searchResultsWidget == null) {
            log.error("Search results widget is null onGrandExchangeSearchBuild");
            return;
        }

        final Widget[] children = searchResultsWidget.getDynamicChildren();
        if (children == null || children.length < 2 || children.length % 3 != 0) {
            return;
        }

        for (int i = 0; i < children.length; i += 3) {
            final Widget itemWidget = children[i + 2];
            final int itemId = itemWidget.getItemId();
            boolean block = false;

            if (preventLocked) {
                try {
                    boolean hasUnlockedItem = itemUnlockService.hasUnlockedItem(itemId);
                    if (!hasUnlockedItem) {
                        block = true;
                    }
                } catch (Exception e) {
                    log.error("Failed to check hasUnlockedItem({}) in onGrandExchangeSearchBuild", itemId, e);
                    return;
                }
            }

            if (!block && preventGear && isGearItem(itemId)) {
                block = true;
            }

            if (block) {
                children[i].setHidden(true);
                children[i + 1].setOpacity(120);
                children[i + 2].setOpacity(120);
            }
        }
    }

    private boolean isGearItem(int itemId) {
        ItemComposition comp = client.getItemDefinition(itemId);
        if (comp == null) {
            return false;
        }
        String[] actions = comp.getInventoryActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action == null) continue;
            String lower = action.toLowerCase();
            if (lower.contains("wear") || lower.contains("equip") || lower.contains("wield")) {
                return true;
            }
        }
        return false;
    }
}
