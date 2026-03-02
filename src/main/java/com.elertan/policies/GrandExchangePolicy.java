package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.PolicyService;
import com.elertan.WorldTypeService;
import com.elertan.models.GameRules;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameStateChanged;
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

        Widget clickedWidget = resolveClickedWidget(event);
        if (clickedWidget == null) {
            return;
        }

        Integer itemId = resolveSearchResultItemId(clickedWidget);
        if (itemId == null || itemId <= 0) {
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

    // Grey out and disable locked items in the GE search results.
    //
    // Widget layout (dynamic children of 162.51 MES_LAYER_SCROLLCONTENTS):
    //   "Previous search" row = 5 children: [0] clickable, [1] text, [2] bg, [3] item icon, [4] separator
    //   Typed search rows    = 3 children each: [i] clickable, [i+1] name, [i+2] item icon
    //
    // We detect the previous-search row via modulo: with it present length%3==2, without it length%3==0.
    private void onSearchBuild() {
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventGrandExchangeBuyOffers)) {
            return;
        }

        Widget searchResultsWidget = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
        if (searchResultsWidget == null) {
            return;
        }

        final Widget[] children = searchResultsWidget.getDynamicChildren();
        if (children == null || children.length < 3) {
            return;
        }

        int startIndex = 0;
        boolean hasPreviousSearchRow = children.length >= 5 && children.length % 3 == 2;

        if (hasPreviousSearchRow) {
            // Previous search row: clickable at [0], item icon at [3]
            applyLockedRowStyling(children, 0, 3, 5);
            startIndex = 5;
        }

        // Typed rows: 3 children each, clickable at [i], item icon at [i+2]
        for (int i = startIndex; i + 2 < children.length; i += 3) {
            applyLockedRowStyling(children, i, i + 2, 3);
        }
    }

    // Hides the clickable widget and dims the row if the item is locked
    private void applyLockedRowStyling(
        Widget[] children,
        int clickableIndex,
        int itemIndex,
        int rowChildCount
    ) {
        Widget clickableWidget = children[clickableIndex];
        Widget itemWidget = children[itemIndex];
        if (clickableWidget == null || itemWidget == null) {
            return;
        }

        final int itemId = itemWidget.getItemId();
        if (itemId <= 0) {
            return;
        }

        final boolean hasUnlockedItem;
        try {
            hasUnlockedItem = itemUnlockService.hasUnlockedItem(itemId);
        } catch (Exception e) {
            log.error("Failed to check hasUnlockedItem({}) in onGrandExchangeSearchBuild", itemId, e);
            return;
        }

        if (hasUnlockedItem) {
            return;
        }

        clickableWidget.setHidden(true);

        // Dim the rest of the row so it looks visually locked
        int rowEndExclusive = Math.min(clickableIndex + rowChildCount, children.length);
        for (int i = clickableIndex + 1; i < rowEndExclusive; i++) {
            Widget rowWidget = children[i];
            if (rowWidget != null) {
                rowWidget.setOpacity(120);
            }
        }
    }

    // event.getWidget() is sometimes null, so fall back to looking it up via param1/param0
    private Widget resolveClickedWidget(MenuOptionClicked event) {
        Widget widget = event.getWidget();
        if (widget != null) {
            return widget;
        }

        Widget rootWidget = client.getWidget(event.getParam1());
        if (rootWidget == null) {
            return null;
        }

        int childIndex = event.getParam0();
        if (childIndex < 0) {
            return rootWidget;
        }

        Widget childWidget = rootWidget.getChild(childIndex);
        return childWidget != null ? childWidget : rootWidget;
    }

    // Walk up from the clicked widget to the scroll container, figure out which row
    // was clicked, and return the item ID for that row.
    private Integer resolveSearchResultItemId(Widget clickedWidget) {
        Widget anchorWidget = clickedWidget;
        Widget parent = anchorWidget.getParent();
        while (parent != null && parent.getId() != InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS) {
            anchorWidget = parent;
            parent = anchorWidget.getParent();
        }

        if (parent == null) {
            return null;
        }

        // Now find which dynamic child index corresponds to our clicked widget.
        Widget[] siblings = parent.getDynamicChildren();
        if (siblings == null || siblings.length < 3) {
            return null;
        }

        int clickedIndex = -1;
        for (int i = 0; i < siblings.length; i++) {
            Widget sibling = siblings[i];
            if (sibling == anchorWidget || (sibling != null && sibling.getId() == anchorWidget.getId())) {
                clickedIndex = i;
                break;
            }
        }

        if (clickedIndex < 0) {
            return null;
        }

        // Same modulo check as onSearchBuild — previous search row = first 5 kids, item at [3]
        boolean hasPreviousSearchRow = siblings.length >= 5 && siblings.length % 3 == 2;
        if (hasPreviousSearchRow && clickedIndex <= 4) {
            Widget itemWidget = siblings[3];
            return itemWidget != null ? itemWidget.getItemId() : null;
        }

        // For typed rows, find which 3-child group this click is in, item is at offset +2
        int itemIndex;
        if (hasPreviousSearchRow) {
            // Skip past the 5 previous-search children
            int rowStart = 5 + ((clickedIndex - 5) / 3) * 3;
            itemIndex = rowStart + 2;
        } else {
            int rowStart = (clickedIndex / 3) * 3;
            itemIndex = rowStart + 2;
        }

        if (itemIndex < 0 || itemIndex >= siblings.length) {
            return null;
        }

        Widget itemWidget = siblings[itemIndex];
        return itemWidget != null ? itemWidget.getItemId() : null;
    }
}
