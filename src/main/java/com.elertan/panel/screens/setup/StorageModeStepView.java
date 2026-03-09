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
import javax.swing.JTextArea;
import javax.swing.border.Border;

public class StorageModeStepView extends JPanel implements AutoCloseable {

    private static final int CONTENT_WIDTH = BUPanel.PANEL_WIDTH - 22;
    private static final Color CARD_BACKGROUND = new Color(39, 39, 39);
    private static final Color CARD_BORDER = new Color(58, 58, 58);
    private static final Color MUTED_TEXT = new Color(145, 145, 145);
    private final StorageModeStepViewModel viewModel;

    public StorageModeStepView(StorageModeStepViewModel viewModel) {
        this.viewModel = viewModel;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Choose your setup");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleLabel.getPreferredSize().height));
        add(titleLabel);
        add(Box.createVerticalStrut(4));

        add(createWrappedTextArea(
            "Choose how you want to store your progress. You can change this later.",
            MUTED_TEXT
        ));
        add(Box.createVerticalStrut(12));

        add(createOptionCard(
            "Play with Group",
            "Use Firebase to sync unlocks and rules with friends.",
            "Best for group play and playing on multiple devices.",
            "Play with Group",
            viewModel::onPlayWithGroupClicked
        ));
        add(Box.createVerticalStrut(10));
        add(createOptionCard(
            "Play Solo",
            "Store your unlocks and rules on this computer.",
            "No groups or device syncing. Your progress stays on this computer only.",
            "Play Solo",
            viewModel::onPlaySoloClicked
        ));
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
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOpaque(true);
        card.setBackground(CARD_BACKGROUND);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        Border outerBorder = BorderFactory.createLineBorder(CARD_BORDER);
        Border innerBorder = BorderFactory.createEmptyBorder(12, 12, 12, 12);
        card.setBorder(BorderFactory.createCompoundBorder(outerBorder, innerBorder));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(4));

        JTextArea descriptionArea = createWrappedTextArea(description, null);
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(descriptionArea);
        card.add(Box.createVerticalStrut(4));

        JTextArea detailArea = createWrappedTextArea(detail, MUTED_TEXT);
        detailArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(detailArea);
        card.add(Box.createVerticalStrut(10));

        JButton button = new JButton(buttonText);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(CONTENT_WIDTH - 48, button.getPreferredSize().height));
        button.addActionListener(e -> onClick.run());
        card.add(button);

        return card;
    }

    private static JTextArea createWrappedTextArea(String text, Color color) {
        JTextArea textArea = new JTextArea(text, 2, 20);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        textArea.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
        if (color != null) {
            textArea.setForeground(color);
        }
        return textArea;
    }
}
