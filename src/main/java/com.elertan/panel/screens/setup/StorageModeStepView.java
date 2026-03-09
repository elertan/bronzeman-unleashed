package com.elertan.panel.screens.setup;

import com.elertan.panel.BUPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.Border;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class StorageModeStepView extends JPanel implements AutoCloseable {

    private static final int CONTENT_WIDTH = BUPanel.PANEL_WIDTH - 12;
    private static final int CARD_WIDTH = CONTENT_WIDTH - 2;
    private static final int CARD_TEXT_WIDTH = CARD_WIDTH - 36;
    private static final Color CARD_BACKGROUND = new Color(39, 39, 39);
    private static final Color CARD_BORDER = new Color(58, 58, 58);
    private static final Color MUTED_TEXT = new Color(145, 145, 145);
    private final StorageModeStepViewModel viewModel;

    public StorageModeStepView(StorageModeStepViewModel viewModel) {
        this.viewModel = viewModel;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("Choose your setup");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setMaximumSize(new Dimension(CONTENT_WIDTH, titleLabel.getPreferredSize().height));
        add(titleLabel);
        add(Box.createVerticalStrut(4));

        add(createWrappedTextPane(
            "Choose how you want to store your progress. You can change this later.",
            MUTED_TEXT,
            CONTENT_WIDTH,
            SwingConstants.CENTER
        ));
        add(Box.createVerticalStrut(12));

        add(createOptionCard(
            "Play Solo",
            "Store your unlocks and rules on this computer.",
            "No groups or device syncing. Your progress stays on this computer only.",
            "Choose Solo",
            viewModel::onPlaySoloClicked
        ));
        add(Box.createVerticalStrut(10));
        add(createOptionCard(
            "Play with Group",
            "Use Firebase to sync unlocks and rules with friends.",
            "Best for group play and playing on multiple devices.",
            "Choose Group",
            viewModel::onPlayWithGroupClicked
        ));
        add(Box.createVerticalGlue());
    }

    @Override
    public void close() {
    }

    private JPanel createOptionCard(
        String title,
        String description,
        String detail,
        String buttonText,
        Runnable onClick
    ) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setOpaque(true);
        card.setBackground(CARD_BACKGROUND);
        card.setMaximumSize(new Dimension(CARD_WIDTH, Integer.MAX_VALUE));

        Border outerBorder = BorderFactory.createLineBorder(CARD_BORDER);
        Border innerBorder = BorderFactory.createEmptyBorder(14, 14, 14, 14);
        card.setBorder(BorderFactory.createCompoundBorder(outerBorder, innerBorder));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setMaximumSize(new Dimension(CARD_WIDTH - 24, titleLabel.getPreferredSize().height));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(8));

        JTextPane descriptionLabel = createWrappedTextPane(
            description,
            null,
            CARD_TEXT_WIDTH,
            SwingConstants.CENTER
        );
        card.add(descriptionLabel);
        card.add(Box.createVerticalStrut(8));

        JTextPane detailLabel = createWrappedTextPane(
            detail,
            MUTED_TEXT,
            CARD_TEXT_WIDTH,
            SwingConstants.CENTER
        );
        card.add(detailLabel);
        card.add(Box.createVerticalStrut(14));

        JButton button = new JButton(buttonText);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(CARD_WIDTH - 28, button.getPreferredSize().height));
        button.addActionListener(e -> onClick.run());
        card.add(button);

        Dimension preferredSize = card.getPreferredSize();
        card.setPreferredSize(new Dimension(CARD_WIDTH, preferredSize.height));
        card.setMaximumSize(new Dimension(CARD_WIDTH, preferredSize.height));
        return card;
    }

    private static JTextPane createWrappedTextPane(String text, Color color, int width, int horizontalAlignment) {
        JTextPane textPane = new JTextPane();
        textPane.setText(text);
        textPane.setEditable(false);
        textPane.setFocusable(false);
        textPane.setOpaque(false);
        textPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        textPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        textPane.setFont(new JLabel().getFont());

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

        textPane.setSize(new Dimension(width, Short.MAX_VALUE));
        Dimension preferredSize = textPane.getPreferredSize();
        textPane.setPreferredSize(new Dimension(width, preferredSize.height));
        textPane.setMaximumSize(new Dimension(width, preferredSize.height));
        return textPane;
    }
}
