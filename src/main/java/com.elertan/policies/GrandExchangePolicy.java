package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.PolicyService;
import com.elertan.WorldTypeService;
import com.elertan.models.GameRules;
import com.elertan.policies.grandexchange.SearchClickResolver;
import com.elertan.policies.grandexchange.SearchResultsParser;
import com.elertan.policies.grandexchange.SearchRow;
import com.elertan.policies.grandexchange.SearchRowStyler;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

@Slf4j
@Singleton
public class GrandExchangePolicy extends PolicyBase {

    // Script 751 fires when typing in the search box, 752 fires for the "show last searched" row.
    private final static int GE_SEARCH_BUILD_SCRIPT_ID = 751;
    private static final int GE_ITEM_SEARCH_SCRIPT_ID = ScriptID.GE_ITEM_SEARCH;

    private final SearchResultsParser searchResultsParser = new SearchResultsParser();
    private final SearchClickResolver searchClickResolver = new SearchClickResolver(searchResultsParser);
    private final SearchRowStyler searchRowStyler = new SearchRowStyler();

    @Inject
    private Client client;
    @Inject
    private ItemUnlockService itemUnlockService;

    // Only process script events when the GE is actually open
    private boolean geInterfaceOpen;

    @Inject
    public GrandExchangePolicy(
        AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService
    ) {
        super(accountConfigurationService, gameRulesService, policyService, worldTypeService);
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

    // Re-apply locked-item styling whenever the GE search results get rebuilt
    public void onScriptPostFired(ScriptPostFired event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        if (!geInterfaceOpen) {
            return;
        }

        int scriptId = event.getScriptId();
        if (scriptId == GE_SEARCH_BUILD_SCRIPT_ID || scriptId == GE_ITEM_SEARCH_SCRIPT_ID) {
            onSearchBuild();
        }
    }

    // Backup click blocking in case the visual hide didn't catch it (race conditions etc)
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        if (!geInterfaceOpen) {
            return;
        }

        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventGrandExchangeBuyOffers)) {
            return;
        }

        Widget clickedWidget = searchClickResolver.resolveClickedWidget(client, event);
        if (clickedWidget == null) {
            return;
        }

        SearchRow clickedRow = searchClickResolver.resolveSearchResultRow(clickedWidget);
        if (clickedRow == null) {
            return;
        }

        int itemId = clickedRow.getItemId();
        if (itemId <= 0) {
            return;
        }

        final boolean hasUnlockedItem;
        try {
            hasUnlockedItem = itemUnlockService.hasUnlockedItem(itemId);
        } catch (Exception e) {
            log.error("Failed to check hasUnlockedItem({}) in onMenuOptionClicked", itemId, e);
            return;
        }

        // Block the click if the item isn't unlocked
        if (!hasUnlockedItem) {
            event.consume();
        }
    }

    private void onSearchBuild() {
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventGrandExchangeBuyOffers)) {
            return;
        }

        Widget searchResultsWidget = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
        if (searchResultsWidget == null) {
            return;
        }

        for (SearchRow row : searchResultsParser.parse(searchResultsWidget)) {
            int itemId = row.getItemId();
            if (itemId <= 0) {
                continue;
            }

            final boolean hasUnlockedItem;
            try {
                hasUnlockedItem = itemUnlockService.hasUnlockedItem(itemId);
            } catch (Exception e) {
                log.error("Failed to check hasUnlockedItem({}) in onGrandExchangeSearchBuild", itemId, e);
                return;
            }

            if (!hasUnlockedItem) {
                searchRowStyler.applyLockedStyle(row);
            }
        }
    }
}
