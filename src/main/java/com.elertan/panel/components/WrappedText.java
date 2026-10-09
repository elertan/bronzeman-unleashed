package com.elertan.panel.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Text that wraps to a fixed width (a non-editable JTextPane sized to its content).
 */
public final class WrappedText {

    private WrappedText() {
    }

    public static JTextPane create(String text, Color color, int width, int horizontalAlignment) {
        return create(text, color, width, horizontalAlignment, null);
    }

    public static JTextPane create(String text, Color color, int width, int horizontalAlignment, Font font) {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFocusable(false);
        textPane.setOpaque(false);
        textPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        textPane.setAlignmentX(horizontalAlignment == SwingConstants.LEFT
            ? Component.LEFT_ALIGNMENT
            : Component.CENTER_ALIGNMENT);
        textPane.setFont(font != null ? font : new JLabel().getFont());
        setText(textPane, text, color, width, horizontalAlignment);
        return textPane;
    }

    public static void setText(JTextPane textPane, String text, Color color, int width, int horizontalAlignment) {
        textPane.setText(text);
        StyledDocument document = textPane.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setAlignment(
            attributes,
            horizontalAlignment == SwingConstants.LEFT ? StyleConstants.ALIGN_LEFT : StyleConstants.ALIGN_CENTER
        );
        if (color != null) {
            StyleConstants.setForeground(attributes, color);
        }
        document.setParagraphAttributes(0, document.getLength(), attributes, false);

        textPane.setPreferredSize(null);
        textPane.setSize(new Dimension(width, Short.MAX_VALUE));
        Dimension preferredSize = textPane.getPreferredSize();
        textPane.setPreferredSize(new Dimension(width, preferredSize.height));
        textPane.setMaximumSize(new Dimension(width, preferredSize.height));
    }
}
