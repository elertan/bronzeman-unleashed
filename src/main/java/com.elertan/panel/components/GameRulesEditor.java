package com.elertan.panel.components;

import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

public class GameRulesEditor extends JPanel {
    private final GameRulesEditorViewModel viewModel;

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        GameRulesEditor create(GameRulesEditorViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public GameRulesEditor create(GameRulesEditorViewModel viewModel) {
            return new GameRulesEditor(viewModel);
        }
    }

    private GameRulesEditor(GameRulesEditorViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;

        JLabel viewOnlyModeLabel = new JLabel("<html><div style=\"text-align:center;color:gray;\">The game rules are in view-only mode. Only the group owner can modify the rules.</div></html>");
        viewOnlyModeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        Bindings.bindVisible(viewOnlyModeLabel, viewModel.isViewOnlyMode);
        add(viewOnlyModeLabel, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(20), gbc);
        gbc.gridy++;

        add(createSection("Trade", "Trade settings", createTradePanel(), true), gbc);
        gbc.gridy++;

        add(createSection("Grand Exchange", "Grand Exchange settings", createGrandExchangePanel(), true), gbc);
        gbc.gridy++;

        add(createSection("Achievements", "Achievements settings", createAchievementsPanel(), true), gbc);
        gbc.gridy++;

        add(createSection("Party", "Controls the party settings", createPartyPanel(), true), gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(20), gbc);
        gbc.gridy++;
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

        JCheckBox preventTradeOutsideGroupCheckBox = new JCheckBox();
        Bindings.bindSelected(preventTradeOutsideGroupCheckBox, viewModel.preventTradeOutsideGroup);
        Bindings.bindEnabled(preventTradeOutsideGroupCheckBox, viewModel.isViewOnlyMode.derive(isViewOnlyMode -> !isViewOnlyMode));
        panel.add(createCheckboxInput("Prevent outside group", "Whether to prevent trading other players that do not belong to the group", preventTradeOutsideGroupCheckBox), gbc);
        gbc.gridy++;

        JCheckBox preventTradeLockedItemsCheckBox = new JCheckBox();
        Bindings.bindSelected(preventTradeLockedItemsCheckBox, viewModel.preventTradeLockedItems);
        Bindings.bindEnabled(preventTradeLockedItemsCheckBox, viewModel.isViewOnlyMode.derive(isViewOnlyMode -> !isViewOnlyMode));
        panel.add(createCheckboxInput("Prevent locked items", "Whether to prevent trading when the other player offers item(s) that are still locked", preventTradeLockedItemsCheckBox), gbc);

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

        JCheckBox preventGrandExchangeBuyOffersCheckbox = new JCheckBox();
        Bindings.bindSelected(preventGrandExchangeBuyOffersCheckbox, viewModel.preventGrandExchangeBuyOffers);
        Bindings.bindEnabled(preventGrandExchangeBuyOffersCheckbox, viewModel.isViewOnlyMode.derive(isViewOnlyMode -> !isViewOnlyMode));
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

        JCheckBox shareAchievementsCheckbox = new JCheckBox();
        Bindings.bindSelected(shareAchievementsCheckbox, viewModel.shareAchievementNotifications);
        Bindings.bindEnabled(shareAchievementsCheckbox, viewModel.isViewOnlyMode.derive(isViewOnlyMode -> !isViewOnlyMode));
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

        JTextField partyPasswordTextField = new JTextField();
        Bindings.bindTextFieldText(partyPasswordTextField, viewModel.partyPassword);
        Bindings.bindEnabled(partyPasswordTextField, viewModel.isViewOnlyMode.derive(isViewOnlyMode -> !isViewOnlyMode));
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
