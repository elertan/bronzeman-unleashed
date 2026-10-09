package com.elertan.panel.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;

/**
 * A status icon and a title, with a hint while the item is still to do.
 */
public final class ChecklistRow extends JPanel {

    private static final Color MUTED_TEXT = new Color(145, 145, 145);
    private static final int ICON_GAP = 8;

    private final StatusIcon icon = new StatusIcon();
    private final JLabel titleLabel = new JLabel();
    private final JTextPane hintText;
    private final int hintWidth;

    public ChecklistRow(int width) {
        hintWidth = width - icon.getIconWidth() - ICON_GAP;
        hintText = WrappedText.create("", MUTED_TEXT, hintWidth, SwingConstants.LEFT);

        setLayout(new BorderLayout(ICON_GAP, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        add(iconLabel, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(titleLabel);
        text.add(hintText);
        add(text, BorderLayout.CENTER);
    }

    /** @param hint may be null */
    public void update(StatusIcon.Status status, String title, String hint) {
        icon.setStatus(status);
        titleLabel.setText(title);
        titleLabel.setForeground(status == StatusIcon.Status.DONE ? MUTED_TEXT : Color.WHITE);
        boolean showHint = status == StatusIcon.Status.TODO && hint != null;
        hintText.setVisible(showHint);
        if (showHint) {
            WrappedText.setText(hintText, hint, MUTED_TEXT, hintWidth, SwingConstants.LEFT);
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        repaint();
    }
}
