package com.elertan.panel;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.Scrollable;

public final class ViewportWidthTrackingPanel extends JPanel implements Scrollable {

    public ViewportWidthTrackingPanel(BorderLayout layout) {
        super(layout);
    }

    @Override
    public java.awt.Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation,
        int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation,
        int direction) {
        return Math.max(visibleRect.height - 16, 16);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // key: constrain width to viewport
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false; // allow vertical scrolling
    }
}
