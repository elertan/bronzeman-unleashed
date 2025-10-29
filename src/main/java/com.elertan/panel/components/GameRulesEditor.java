package com.elertan.panel.components;

import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.NumberFormatter;
import net.runelite.client.ui.ColorScheme;

public class GameRulesEditor extends JPanel {

    private final GameRulesEditorViewModel viewModel;

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

        JLabel viewOnlyModeLabel = new JLabel(
            "<html><div style=\"text-align:center;color:gray;\">The game rules are in view-only mode. Only the group owner can modify the rules.</div></html>");
        viewOnlyModeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        Bindings.bindVisible(viewOnlyModeLabel, viewModel.isViewOnlyModeProperty);
        add(viewOnlyModeLabel, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(20), gbc);
        gbc.gridy++;

        add(createSection("Trade", "Trade settings", createTradePanel(), true), gbc);
        gbc.gridy++;

        add(
            createSection(
                "Grand Exchange",
                "Grand Exchange settings",
                createGrandExchangePanel(),
                true
            ), gbc
        );
        gbc.gridy++;

        add(
            createSection(
                "Notifications",
                "Notification settings",
                createNotificationsPanel(),
                true
            ),
            gbc
        );
        gbc.gridy++;

        add(createSection("Party", "Controls the party settings", createPartyPanel(), true), gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(20), gbc);
        gbc.gridy++;
    }

    private JPanel createSection(String title, String description, JComponent content,
        boolean defaultExpanded) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);

        JToggleButton headerButton = new JToggleButton(createColoredToggleButtonText(
            defaultExpanded,
            title
        ));
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
        return "<html><div style=\"text-align:left;color:rgb(220,138,0);\">" + expandedText + title
            + "</div></html>";
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
        Bindings.bindSelected(
            preventTradeOutsideGroupCheckBox,
            viewModel.preventTradeOutsideGroupProperty
        );
        Bindings.bindEnabled(
            preventTradeOutsideGroupCheckBox,
            viewModel.isViewOnlyModeProperty.derive(isViewOnlyMode -> !isViewOnlyMode)
        );
        panel.add(
            createCheckboxInput(
                "Prevent outside group",
                "Whether to prevent trading other players that do not belong to the group",
                preventTradeOutsideGroupCheckBox
            ), gbc
        );
        gbc.gridy++;

        JCheckBox preventTradeLockedItemsCheckBox = new JCheckBox();
        Bindings.bindSelected(
            preventTradeLockedItemsCheckBox,
            viewModel.preventTradeLockedItemsProperty
        );
        Bindings.bindEnabled(
            preventTradeLockedItemsCheckBox,
            viewModel.isViewOnlyModeProperty.derive(isViewOnlyMode -> !isViewOnlyMode)
        );
        panel.add(
            createCheckboxInput(
                "Prevent locked items",
                "Whether to prevent trading when the other player offers item(s) that are still locked",
                preventTradeLockedItemsCheckBox
            ), gbc
        );

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
        Bindings.bindSelected(
            preventGrandExchangeBuyOffersCheckbox,
            viewModel.preventGrandExchangeBuyOffersProperty
        );
        Bindings.bindEnabled(
            preventGrandExchangeBuyOffersCheckbox,
            viewModel.isViewOnlyModeProperty.derive(isViewOnlyMode -> !isViewOnlyMode)
        );
        panel.add(
            createCheckboxInput(
                "Prevent buy offers",
                "Whether to prevent buying items on the Grand Exchange that are still locked",
                preventGrandExchangeBuyOffersCheckbox
            ), gbc
        );

        return panel;
    }

    private JPanel createNotificationsPanel() {
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
        Bindings.bindSelected(
            shareAchievementsCheckbox,
            viewModel.shareAchievementNotificationsProperty
        );
        Bindings.bindEnabled(
            shareAchievementsCheckbox,
            viewModel.isViewOnlyModeProperty.derive(isViewOnlyMode -> !isViewOnlyMode)
        );
        panel.add(
            createCheckboxInput(
                "Share achievements",
                "Whether to share achievements in the chat to other members (level ups, quest completions, combat tasks and more...)",
                shareAchievementsCheckbox
            ), gbc
        );
        gbc.gridy++;

        // Integer spinner with US comma grouping and loose mid-typing, strict commit
        JSpinner valuableLootThresholdSpinner = new JSpinner(
            new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 100)
        );
        valuableLootThresholdSpinner.addChangeListener(e -> {

        });

        // Force US comma formatting and integer-only behavior
        JSpinner.NumberEditor numberEditor = new JSpinner.NumberEditor(
            valuableLootThresholdSpinner,
            "#,##0"
        );
        valuableLootThresholdSpinner.setEditor(numberEditor);
        DecimalFormat df = numberEditor.getFormat();
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        df.setGroupingUsed(true);

        // Configure the formatter for loose mid-typing and strict commit
        JFormattedTextField editorField = numberEditor.getTextField();
        if (editorField.getFormatter() instanceof NumberFormatter) {
            NumberFormatter nf = (NumberFormatter) editorField.getFormatter();
            nf.setValueClass(Integer.class);
            nf.setAllowsInvalid(true);          // allow temporary invalid edits while typing
            nf.setCommitsOnValidEdit(true);     // commit and reformat on valid edits
            nf.setMinimum(0);
            nf.setMaximum(Integer.MAX_VALUE);
            nf.setOverwriteMode(false);
        }

        Bindings.bindEnabled(
            valuableLootThresholdSpinner,
            viewModel.isViewOnlyModeProperty.derive(isViewOnlyMode -> !isViewOnlyMode)
        );
        Bindings.bindSpinner(
            valuableLootThresholdSpinner,
            viewModel.valuableLootNotificationThresholdProperty
        );
        panel.add(
            createSpinnerInput(
                "Valuable loot threshold",
                "Set the coins value threshold for valuable loot to be shared in the chat",
                valuableLootThresholdSpinner
            ), gbc
        );

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
        Bindings.bindTextFieldText(partyPasswordTextField, viewModel.partyPasswordProperty);
        Bindings.bindEnabled(
            partyPasswordTextField,
            viewModel.isViewOnlyModeProperty.derive(isViewOnlyMode -> !isViewOnlyMode)
        );
        panel.add(
            createTextFieldInput(
                "Party password",
                "When auto-join is enabled in the plugin configuration, use this password to join the group's party",
                partyPasswordTextField
            ), gbc
        );

        return panel;
    }

    private JPanel createTextFieldInput(String labelText, String description,
        JTextField textField) {
        JPanel inputPanel = new JPanel(new GridBagLayout());

        inputPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 5, 0);

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setToolTipText(description);
        inputPanel.add(label, gbc);
        gbc.gridy++;

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        inputPanel.add(textField, gbc);

        return inputPanel;
    }

    private JPanel createSpinnerInput(String labelText, String description, JSpinner spinner) {
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 5, 0);

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setToolTipText(description);
        inputPanel.add(label, gbc);
        gbc.gridy++;

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        inputPanel.add(spinner, gbc);
        return inputPanel;
    }

    private JPanel createCheckboxInput(String labelText, String description, JCheckBox checkBox) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setToolTipText(description);
        panel.add(label);

        panel.add(Box.createHorizontalGlue());

        checkBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        panel.add(checkBox);

        return panel;
    }

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
}
