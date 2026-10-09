package com.elertan.panel.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JPanel;

/**
 * A thin progress bar: orange while in progress, green when complete.
 */
public final class ProgressBar extends JPanel {

    private static final Color TRACK = new Color(58, 58, 58);

    private float progress;

    public ProgressBar(int width, int height) {
        setOpaque(false);
        Dimension size = new Dimension(width, height);
        setPreferredSize(size);
        setMaximumSize(size);
    }

    /** @param progress from 0 to 1 */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(TRACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(progress >= 1f ? StatusIcon.DONE_COLOR : StatusIcon.TODO_COLOR);
        g.fillRect(0, 0, Math.round(getWidth() * progress), getHeight());
    }
}
