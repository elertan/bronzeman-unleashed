package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.models.GameRules;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

@Slf4j
@Singleton
public class GrandExchangePolicy extends PolicyBase implements BUPluginLifecycle {
    private final static int GE_SEARCH_BUILD_SCRIPT_ID = 751;

    @Inject
    private Client client;
    @Inject
    private ItemUnlockService itemUnlockService;

    private final GameRulesService gameRulesService;

    @Inject
    public GrandExchangePolicy(AccountConfigurationService accountConfigurationService, GameRulesService gameRulesService) {
        super(accountConfigurationService, gameRulesService);

        this.gameRulesService = gameRulesService;
    }

    @Override
    public void startUp() throws Exception {

    }

    @Override
    public void shutDown() throws Exception {

    }

    public void onScriptPostFired(ScriptPostFired event) {
        int scriptId = event.getScriptId();
        if (scriptId == GE_SEARCH_BUILD_SCRIPT_ID) {
            onSearchBuild();
        }
    }

    private void onSearchBuild() {
        if (!shouldEnforcePolicies()) {
            return;
        }
        GameRules gameRules = gameRulesService.getGameRules();
        if (!gameRules.isPreventGrandExchangeBuyOffers()) {
            return;
        }

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
            final boolean hasUnlockedItem;
            try {
                hasUnlockedItem = itemUnlockService.hasUnlockedItem(itemId);
            } catch (Exception e) {
                log.error("Failed to check hasUnlockedItem({}) in onGrandExchangeSearchBuild", itemId, e);
                return;
            }

            if (!hasUnlockedItem) {
                // Make not clickable
                children[i].setHidden(true);

                // Make transparent to indicate not clickable
                children[i + 1].setOpacity(120);
                children[i + 2].setOpacity(120);
            }
        }

    }
}
