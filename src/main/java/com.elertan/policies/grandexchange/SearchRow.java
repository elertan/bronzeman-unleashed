package com.elertan.policies.grandexchange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.widgets.Widget;

public class SearchRow {

    private final int itemId;
    private final Widget clickableWidget;
    private final List<Widget> rowWidgets;
    private final List<Widget> visualWidgets;

    public SearchRow(
        int itemId,
        Widget clickableWidget,
        List<Widget> rowWidgets,
        List<Widget> visualWidgets
    ) {
        this.itemId = itemId;
        this.clickableWidget = clickableWidget;
        this.rowWidgets = Collections.unmodifiableList(new ArrayList<>(rowWidgets));
        this.visualWidgets = Collections.unmodifiableList(new ArrayList<>(visualWidgets));
    }

    public int getItemId() {
        return itemId;
    }

    public Widget getClickableWidget() {
        return clickableWidget;
    }

    public List<Widget> getVisualWidgets() {
        return visualWidgets;
    }

    public boolean containsWidget(Widget candidateWidget) {
        if (candidateWidget == null) {
            return false;
        }

        for (Widget rowWidget : rowWidgets) {
            if (rowWidget == candidateWidget) {
                return true;
            }

            if (rowWidget != null && rowWidget.getId() == candidateWidget.getId()) {
                return true;
            }
        }

        return false;
    }
}
