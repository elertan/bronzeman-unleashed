package com.elertan.panel.components;

import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.NumberFormatter;

public class GameRulesEditor extends JPanel {

    private GameRulesEditor(GameRulesEditorViewModel viewModel) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        JLabel viewOnlyLabel = new JLabel(
            "<html><div style=\"text-align:center;color:gray;\">The game rules are in view-only mode. Only the group owner can modify the rules.</div></html>");
        viewOnlyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        Bindings.bindVisible(viewOnlyLabel, viewModel.isViewOnlyModeProperty);
        add(viewOnlyLabel);
        add(Box.createVerticalStrut(20));

        Property<Boolean> editable = viewModel.isViewOnlyModeProperty.derive(v -> !v);

        add(new FormBuilder()
            .section("General")
            .checkbox("Only for tradeable items",
                "Whether to only unlock items that are tradeable (reduces a lot of clutter for e.g. quest items)",
                viewModel.onlyForTradeableItemsProperty, editable)
            .section("Ground items")
            .checkbox("Restrict ground items",
                "Whether to only allow taking items that are spawns, belong to you, or your bronzeman group members.",
                viewModel.restrictGroundItemsProperty, editable)
            .section("Trade")
            .checkbox("Prevent outside group",
                "Whether to prevent trading other players that do not belong to the group",
                viewModel.preventTradeOutsideGroupProperty, editable)
            .section("Grand Exchange")
            .checkbox("Prevent buy offers",
                "Whether to prevent buying items on the Grand Exchange that are still locked",
                viewModel.preventGrandExchangeBuyOffersProperty, editable)
            .checkbox("Prevent gear buys (consumables only)",
                "When enabled, equipment and other wearable items cannot be bought on the Grand Exchange, "
                    + "even if they are unlocked. Only consumables (food, potions, etc.) remain purchasable.",
                viewModel.preventGrandExchangeGearBuyOffersProperty, editable)
            .section("Player Owned House (POH)")
            .checkbox("Restrict POH usage",
                "Restrict using a POH that isn't yours or a group member's",
                viewModel.preventPlayedOwnedHouseProperty, editable)
            .section("Player vs. Player (PvP)")
            .checkbox("Restrict loot",
                "Restricts all loot from drops when killing other players or when opening loot keys",
                viewModel.restrictPlayerVersusPlayerLootProperty, editable)
            .section("Falador Party Room")
            .checkbox("Restrict balloons",
                "Restricts bursting the balloons in the Falador Party Room",
                viewModel.restrictFaladorPartyRoomBalloonsProperty, editable)
            .build());

        add(buildNotificationsPanel(viewModel, editable));
        add(buildPartyPanel(viewModel, editable));
    }

    private static JPanel buildNotificationsPanel(GameRulesEditorViewModel vm,
        Property<Boolean> editable) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        c.gridy = 0;
        c.insets = new Insets(8, 0, 2, 0);
        panel.add(new JSeparator(), c);

        JLabel header = new JLabel("Notifications");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        c.gridy = 1;
        c.insets = new Insets(4, 0, 4, 0);
        panel.add(header, c);

        JLabel label = new JLabel("Valuable loot threshold");
        label.setForeground(Color.WHITE);
        label.setToolTipText(
            "Set the coins value threshold for valuable loot to be shared in the chat (set to 0 to disable)");
        c.gridy = 2;
        c.insets = new Insets(0, 0, 2, 0);
        panel.add(label, c);

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 100));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#,##0");
        spinner.setEditor(editor);
        editor.getFormat().setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        editor.getFormat().setGroupingUsed(true);
        JFormattedTextField tf = editor.getTextField();
        if (tf.getFormatter() instanceof NumberFormatter) {
            NumberFormatter nf = (NumberFormatter) tf.getFormatter();
            nf.setValueClass(Integer.class);
            nf.setAllowsInvalid(true);
            nf.setCommitsOnValidEdit(true);
            nf.setMinimum(0);
            nf.setMaximum(Integer.MAX_VALUE);
            nf.setOverwriteMode(false);
        }
        Bindings.bindEnabled(spinner, editable);
        Bindings.bindSpinner(spinner, vm.valuableLootNotificationThresholdProperty);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(spinner, c);

        return panel;
    }

    private static JPanel buildPartyPanel(GameRulesEditorViewModel vm,
        Property<Boolean> editable) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        c.gridy = 0;
        c.insets = new Insets(8, 0, 2, 0);
        panel.add(new JSeparator(), c);

        JLabel header = new JLabel("Party");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        c.gridy = 1;
        c.insets = new Insets(4, 0, 4, 0);
        panel.add(header, c);

        JLabel label = new JLabel("Party password");
        label.setForeground(Color.WHITE);
        label.setToolTipText(
            "When auto-join is enabled in the plugin configuration, use this password to join the group's party");
        c.gridy = 2;
        c.insets = new Insets(0, 0, 2, 0);
        panel.add(label, c);

        JPasswordField passwordField = new JPasswordField();
        Bindings.bindTextFieldText(passwordField, vm.partyPasswordProperty);
        Bindings.bindEnabled(passwordField, editable);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(passwordField, c);

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
