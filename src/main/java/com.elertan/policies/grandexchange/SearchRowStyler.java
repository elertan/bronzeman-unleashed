package com.elertan.policies.grandexchange;

import net.runelite.api.widgets.Widget;

public class SearchRowStyler {

    private static final int LOCKED_ROW_OPACITY = 120;

    public void applyLockedStyle(SearchRow row) {
        Widget clickableWidget = row.getClickableWidget();
        if (clickableWidget == null) {
            return;
        }

        clickableWidget.setHidden(true);

        for (Widget visualWidget : row.getVisualWidgets()) {
            if (visualWidget != null) {
                visualWidget.setOpacity(LOCKED_ROW_OPACITY);
            }
        }
    }
}
