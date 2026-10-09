package com.elertan.panel.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * A round status icon: a green check (done), an orange ring (to do) or a grey ring with dots
 * (waiting). Drawn in code, because the RuneLite font has no check or cross characters.
 */
public final class StatusIcon implements Icon {

    public static final Color DONE_COLOR = new Color(46, 160, 67);
    public static final Color TODO_COLOR = new Color(220, 138, 0);
    public static final Color WAITING_COLOR = new Color(145, 145, 145);
    private static final int SIZE = 16;

    private Status status = Status.TODO;

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (status) {
            case DONE:
                g2.setColor(DONE_COLOR);
                g2.fillOval(x, y, SIZE, SIZE);
                g2.setColor(Color.WHITE);
                g2.drawPolyline(new int[]{x + 4, x + 7, x + 12}, new int[]{y + 8, y + 11, y + 5}, 3);
                break;
            case TODO:
                g2.setColor(TODO_COLOR);
                g2.drawOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
                break;
            case WAITING:
            default:
                g2.setColor(WAITING_COLOR);
                g2.drawOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
                for (int i = 0; i < 3; i++) {
                    g2.fillOval(x + 4 + i * 3, y + 7, 2, 2);
                }
                break;
        }
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    public enum Status {
        DONE,
        TODO,
        WAITING
    }
}
