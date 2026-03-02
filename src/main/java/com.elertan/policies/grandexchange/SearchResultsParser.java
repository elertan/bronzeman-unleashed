package com.elertan.policies.grandexchange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.widgets.Widget;

public class SearchResultsParser {

    private static final int MIN_ROW_CHILDREN = 3;
    private static final int PREVIOUS_SEARCH_ROW_CHILDREN = 5;
    private static final int PREVIOUS_SEARCH_ROW_ITEM_OFFSET = 3;
    private static final int TYPED_SEARCH_ROW_CHILDREN = 3;
    private static final int TYPED_SEARCH_ROW_ITEM_OFFSET = 2;

    public List<SearchRow> parse(Widget searchResultsWidget) {
        if (searchResultsWidget == null) {
            return Collections.emptyList();
        }

        return parse(searchResultsWidget.getDynamicChildren());
    }

    private List<SearchRow> parse(Widget[] children) {
        if (children == null || children.length < MIN_ROW_CHILDREN) {
            return Collections.emptyList();
        }

        List<SearchRow> rows = new ArrayList<>();

        int startIndex = 0;
        if (hasPreviousSearchRow(children)) {
            addRow(
                rows,
                children,
                0,
                PREVIOUS_SEARCH_ROW_CHILDREN,
                PREVIOUS_SEARCH_ROW_ITEM_OFFSET
            );
            startIndex = PREVIOUS_SEARCH_ROW_CHILDREN;
        }

        for (int i = startIndex; i + TYPED_SEARCH_ROW_ITEM_OFFSET < children.length; i +=
            TYPED_SEARCH_ROW_CHILDREN) {
            addRow(rows, children, i, TYPED_SEARCH_ROW_CHILDREN, TYPED_SEARCH_ROW_ITEM_OFFSET);
        }

        return rows;
    }

    private boolean hasPreviousSearchRow(Widget[] children) {
        return children.length >= PREVIOUS_SEARCH_ROW_CHILDREN && children.length
            % TYPED_SEARCH_ROW_CHILDREN == 2;
    }

    private void addRow(
        List<SearchRow> rows,
        Widget[] children,
        int rowStartIndex,
        int rowChildCount,
        int itemOffset
    ) {
        if (rowStartIndex < 0 || rowStartIndex >= children.length) {
            return;
        }

        int rowEndExclusive = Math.min(rowStartIndex + rowChildCount, children.length);
        if (rowEndExclusive <= rowStartIndex) {
            return;
        }

        Widget clickableWidget = children[rowStartIndex];

        int itemIndex = rowStartIndex + itemOffset;
        Widget itemWidget = itemIndex < rowEndExclusive ? children[itemIndex] : null;
        int itemId = itemWidget != null ? itemWidget.getItemId() : -1;

        List<Widget> rowWidgets = new ArrayList<>();
        List<Widget> visualWidgets = new ArrayList<>();
        for (int i = rowStartIndex; i < rowEndExclusive; i++) {
            Widget widget = children[i];
            rowWidgets.add(widget);
            if (i > rowStartIndex) {
                visualWidgets.add(widget);
            }
        }

        rows.add(new SearchRow(itemId, clickableWidget, rowWidgets, visualWidgets));
    }
}
