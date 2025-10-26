package com.elertan.panel.accountConfiguration;

import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

public class GameRulesConfigurationView extends JPanel {
    public interface Listener {
        void onBack();

        CompletableFuture<Void> onSuccess(GameRules rules, boolean isViewOnlyMode);
    }

    private final long accountHash;
    private final Listener listener;

    private GameRules gameRules;
    private boolean isSubmitting = false;
    private boolean isViewOnlyMode = false;

    private final JButton backButton = new JButton("Go back");
    private final JButton finishButton = new JButton("Finish");

    private final JLabel viewOnlyModeLabel = new JLabel("<html><div style=\"text-align:center;color:gray;\">The game rules are in view-only mode. Only the group owner can modify the rules.</div></html>");

    // Trade
    private JCheckBox preventTradeOutsideGroupCheckbox = new JCheckBox();
    private JCheckBox preventTradeLockedItemsCheckbox = new JCheckBox();

    // Grand Exchange
    private JCheckBox preventGrandExchangeBuyOffersCheckbox = new JCheckBox();

    // Achievements
    private JCheckBox shareAchievementsCheckbox = new JCheckBox();

    // Party
    private JCheckBox joinPartyOnLoginCheckBox = new JCheckBox();
    private JTextField partyPasswordTextField = new JTextField(15);

    public GameRulesConfigurationView(long accountHash, Listener listener) {
        this.accountHash = accountHash;
        this.listener = listener;

        this.gameRules = GameRules.createWithDefaults(accountHash, new ISOOffsetDateTime(OffsetDateTime.now()));

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JLabel titleLabel = new JLabel("Game Rules");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(10));
        viewOnlyModeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(viewOnlyModeLabel);
        header.add(Box.createVerticalStrut(10));
        add(header, BorderLayout.NORTH);

        // Center container for collapsible sections
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        add(center, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.PAGE_START;

        center.add(createSection("Trade", "Trade settings", createTradePanel(), true), gbc);
        gbc.gridy++;

        center.add(createSection("Grand Exchange", "Grand Exchange settings", createGrandExchangePanel(), true), gbc);
        gbc.gridy++;

        center.add(createSection("Achievements", "Achievements settings", createAchievementsPanel(), true), gbc);
        gbc.gridy++;

        center.add(createSection("Party", "Controls the party settings", createPartyPanel(), true), gbc);
        gbc.gridy++;

        center.add(Box.createVerticalStrut(20), gbc);
        gbc.gridy++;

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);

        backButton.addActionListener(e -> listener.onBack());
        buttonRow.add(backButton);

        buttonRow.add(Box.createHorizontalGlue());

        finishButton.addActionListener(e -> {
            setIsSubmitting(true);
            listener.onSuccess(this.gameRules, isViewOnlyMode).whenComplete((__, throwable) -> {
                setIsSubmitting(false);
            });
        });
        buttonRow.add(finishButton);

        center.add(buttonRow, gbc);

        // Filler to keep everything pinned to the top
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        gbc.gridy++;
        gbc.weighty = 1.0;
        center.add(filler, gbc);
    }

    public void applyGameRules(GameRules gameRules) {
        if (gameRules == null) {
            this.gameRules = GameRules.createWithDefaults(this.accountHash, new ISOOffsetDateTime(OffsetDateTime.now()));
            // This means we have to configure the rules
            preventTradeOutsideGroupCheckbox.setEnabled(true);
            preventTradeLockedItemsCheckbox.setEnabled(true);
            preventGrandExchangeBuyOffersCheckbox.setEnabled(true);
            shareAchievementsCheckbox.setEnabled(true);
            partyPasswordTextField.setEnabled(true);

            viewOnlyModeLabel.setVisible(false);
            isViewOnlyMode = false;
        } else {
            this.gameRules = gameRules;

            // This means we can only view the rules here
            preventTradeOutsideGroupCheckbox.setEnabled(false);
            preventTradeLockedItemsCheckbox.setEnabled(false);
            preventGrandExchangeBuyOffersCheckbox.setEnabled(false);
            shareAchievementsCheckbox.setEnabled(false);
            partyPasswordTextField.setEnabled(false);

            viewOnlyModeLabel.setVisible(true);
            isViewOnlyMode = true;
        }

        preventTradeOutsideGroupCheckbox.setSelected(this.gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItemsCheckbox.setSelected(this.gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersCheckbox.setSelected(this.gameRules.isPreventGrandExchangeBuyOffers());
        shareAchievementsCheckbox.setSelected(this.gameRules.isShareAchievementNotifications());
        partyPasswordTextField.setText(this.gameRules.getPartyPassword());

        revalidate();
        repaint();
    }

    private void setIsSubmitting(boolean isSubmitting) {
        if (this.isSubmitting == isSubmitting) {
            return;
        }
        this.isSubmitting = isSubmitting;

        if (isSubmitting) {
            backButton.setEnabled(false);
            finishButton.setEnabled(false);
        } else {
            backButton.setEnabled(true);
            finishButton.setEnabled(true);
        }

        revalidate();
        repaint();
    }

    private JPanel createSection(String title, String description, JComponent content, boolean defaultExpanded) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);

        JToggleButton headerButton = new JToggleButton(createColoredToggleButtonText(defaultExpanded, title));
        headerButton.setFont(headerButton.getFont().deriveFont(Font.BOLD, 16f));
        headerButton.setFocusPainted(false);
        headerButton.setContentAreaFilled(false);
        headerButton.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        headerButton.setSelected(defaultExpanded);
        headerButton.setHorizontalAlignment(SwingConstants.LEFT);
        headerButton.setHorizontalTextPosition(SwingConstants.LEFT);
        headerButton.setToolTipText(description);

        JPanel paddedContent = new JPanel();
        paddedContent.setLayout(new BoxLayout(paddedContent, BoxLayout.Y_AXIS));
        paddedContent.setOpaque(false);
        paddedContent.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        paddedContent.add(content);

        paddedContent.setVisible(defaultExpanded);

        // i didnt want rewards anyways
        content.setOpaque(false);

        headerButton.addActionListener(e -> {
//            boolean expanded = headerButton.isSelected();
//            headerButton.setText(createColoredToggleButtonText(expanded, title));
//            paddedContent.setVisible(expanded);
//            outer.revalidate();
//            outer.repaint();
        });

        outer.add(headerButton, BorderLayout.NORTH);

        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(new Color(85, 85, 85)); // similar to RuneLite-style line color
        separator.setBackground(new Color(40, 40, 40)); // darker background for contrast
        outer.add(separator, BorderLayout.CENTER);

        outer.add(paddedContent, BorderLayout.SOUTH);
        return outer;
    }

    private String createColoredToggleButtonText(boolean expanded, String title) {
//        String expandedText = (expanded ? "X " : "V ");
        String expandedText = "";
        return "<html><div style=\"text-align:left;color:rgb(220,138,0);\">" + expandedText + title + "</div></html>";
    }

    private JPanel createTradePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        preventTradeOutsideGroupCheckbox.setSelected(gameRules.isPreventTradeOutsideGroup());
        panel.add(createCheckboxInput("Prevent outside group", "Whether to prevent trading other players that do not belong to the group", preventTradeOutsideGroupCheckbox), gbc);
        gbc.gridy++;

        preventTradeLockedItemsCheckbox.setSelected(gameRules.isPreventTradeLockedItems());
        panel.add(createCheckboxInput("Prevent locked items", "Whether to prevent trading when the other player offers item(s) that are still locked", preventTradeLockedItemsCheckbox), gbc);

        return panel;
    }

    private JPanel createGrandExchangePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        preventGrandExchangeBuyOffersCheckbox.setSelected(gameRules.isPreventGrandExchangeBuyOffers());
        panel.add(createCheckboxInput("Prevent buy offers", "Whether to prevent buying items on the Grand Exchange that are still locked", preventGrandExchangeBuyOffersCheckbox), gbc);

        return panel;
    }

    private JPanel createAchievementsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        shareAchievementsCheckbox.setSelected(gameRules.isShareAchievementNotifications());
        panel.add(createCheckboxInput("Share achievements", "Whether to share achievements in the chat to other members (level ups, quest completions, combat tasks and more...)", shareAchievementsCheckbox), gbc);

        return panel;
    }


    private JPanel createPartyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        partyPasswordTextField.setText(gameRules.getPartyPassword());
        partyPasswordTextField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                String input = partyPasswordTextField.getText().trim();
                if (input.isEmpty()) {
                    input = null;
                }
                gameRules.setPartyPassword(input);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        panel.add(createTextFieldInput("Party password", "When auto-join is enabled in the plugin configuration, use this password to join the group's party", partyPasswordTextField), gbc);

        return panel;
    }

    private JPanel createTextFieldInput(String labelText, String description, JTextField textField) {
        JPanel inputPanel = new JPanel(new GridBagLayout());

        inputPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 5, 0);

        JLabel label = new JLabel(labelText);
        label.setToolTipText(description);
        inputPanel.add(label, gbc);
        gbc.gridy++;

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        inputPanel.add(textField, gbc);

        return inputPanel;
    }

    private JPanel createCheckboxInput(String labelText, String description, JCheckBox checkBox) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        JLabel label = new JLabel(labelText);
        label.setToolTipText(description);
        panel.add(label);

        panel.add(Box.createHorizontalGlue());

        checkBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        panel.add(checkBox);

        return panel;
    }
}
