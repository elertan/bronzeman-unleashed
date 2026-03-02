package com.elertan.policies.grandexchange;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public class SearchClickResolver {

    private final SearchResultsParser searchResultsParser;

    public SearchClickResolver(SearchResultsParser searchResultsParser) {
        this.searchResultsParser = searchResultsParser;
    }

    public Widget resolveClickedWidget(Client client, MenuOptionClicked event) {
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

    public SearchRow resolveSearchResultRow(Widget clickedWidget) {
        SearchResultsAnchor anchor = resolveAnchor(clickedWidget);
        if (anchor == null) {
            return null;
        }

        List<SearchRow> rows = searchResultsParser.parse(anchor.scrollContentsWidget);
        for (SearchRow row : rows) {
            if (row.containsWidget(anchor.anchorWidget)) {
                return row;
            }
        }

        return null;
    }

    // Match the same ancestor climb as the original policy: walk up to chatbox scroll contents
    // and keep the highest descendant that is still in the clicked row.
    private SearchResultsAnchor resolveAnchor(Widget clickedWidget) {
        Widget anchorWidget = clickedWidget;
        Widget parent = anchorWidget.getParent();
        while (parent != null && parent.getId() != InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS) {
            anchorWidget = parent;
            parent = anchorWidget.getParent();
        }

        if (parent == null) {
            return null;
        }

        return new SearchResultsAnchor(parent, anchorWidget);
    }

    private static class SearchResultsAnchor {

        private final Widget scrollContentsWidget;
        private final Widget anchorWidget;

        private SearchResultsAnchor(Widget scrollContentsWidget, Widget anchorWidget) {
            this.scrollContentsWidget = scrollContentsWidget;
            this.anchorWidget = anchorWidget;
        }
    }
}
