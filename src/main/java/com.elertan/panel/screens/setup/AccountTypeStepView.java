package com.elertan.panel.screens.setup;

import com.elertan.models.StartMode;
import com.elertan.panel.BUPanel;
import com.elertan.panel.components.WrappedText;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

/**
 * Setup step "Your account": new or existing account.
 */
public class AccountTypeStepView extends JPanel implements AutoCloseable {

    private static final int CONTENT_WIDTH = BUPanel.PANEL_WIDTH - 12;
    private static final int CARD_WIDTH = CONTENT_WIDTH - 2;
    private static final int CARD_PADDING = 12;
    private static final int CARD_TEXT_WIDTH = CARD_WIDTH - 2 * CARD_PADDING - 4;
    private static final int WARNING_TEXT_WIDTH = CARD_WIDTH - 2 - 2 * 10;
    private static final int BULLET_TEXT_WIDTH = WARNING_TEXT_WIDTH - 12;
    private static final Color CARD_BACKGROUND = new Color(39, 39, 39);
    private static final Color CARD_BORDER = new Color(58, 58, 58);
    private static final Color SELECTED = new Color(220, 138, 0);
    private static final Color MUTED_TEXT = new Color(145, 145, 145);
    private static final Color DISABLED_TEXT = new Color(100, 100, 100);
    private static final Color WARNING = new Color(255, 200, 90);
    private static final Color WARNING_BACKGROUND = new Color(50, 43, 30);
    private static final Color WARNING_BORDER = new Color(92, 76, 42);

    private final AccountTypeStepViewModel viewModel;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private final OptionCard newAccountCard;
    private final OptionCard existingAccountCard;
    private final PropertyChangeListener refreshListener = e -> Bindings.invokeOnEDT(this::refreshCards);

    public AccountTypeStepView(AccountTypeStepViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JLabel titleLabel = new JLabel("Your account");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(4));
        add(WrappedText.create(
            "How are you starting Bronzeman on this account?",
            MUTED_TEXT,
            CONTENT_WIDTH,
            SwingConstants.CENTER
        ));
        add(Box.createVerticalStrut(12));

        newAccountCard = new OptionCard(
            "New account",
            "Everything you own counts as unlocked.",
            "Classic Bronzeman, for an account that starts from nothing.",
            () -> viewModel.onStartModeSelected(StartMode.NEW_ACCOUNT)
        );
        existingAccountCard = new OptionCard(
            "Existing account",
            "Items you already own are locked in your bank.",
            "Only what you get from now on unlocks and can be used.",
            () -> viewModel.onStartModeSelected(StartMode.EXISTING_ACCOUNT)
        );
        add(newAccountCard);
        add(Box.createVerticalStrut(8));
        add(existingAccountCard);
        add(Box.createVerticalStrut(8));

        JTextPane requiredNote = WrappedText.create(
            "This group requires new members to lock their items.",
            WARNING,
            CONTENT_WIDTH,
            SwingConstants.CENTER
        );
        bindings.add(Bindings.bindVisible(requiredNote, Property.deriveMany(
            Arrays.asList(viewModel.isExistingAccountSaved, viewModel.isItemLockRequired),
            values -> !Boolean.TRUE.equals(values.get(0)) && Boolean.TRUE.equals(values.get(1))
        )));
        add(requiredNote);

        JTextPane savedNote = WrappedText.create(
            "You made this choice when you joined. Leave Bronzeman to choose again.",
            WARNING,
            CONTENT_WIDTH,
            SwingConstants.CENTER
        );
        bindings.add(Bindings.bindVisible(savedNote, viewModel.isExistingAccountSaved));
        add(savedNote);

        JPanel warningBox = createWarningBox();
        bindings.add(Bindings.bindVisible(warningBox, Property.deriveMany(
            Arrays.asList(viewModel.selectedStartMode, viewModel.isExistingAccountSaved),
            values -> values.get(0) == StartMode.EXISTING_ACCOUNT && !Boolean.TRUE.equals(values.get(1))
        )));
        add(Box.createVerticalStrut(4));
        add(warningBox);

        JLabel errorLabel = new JLabel();
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        bindings.add(Bindings.bindLabelText(errorLabel, viewModel.errorMessage.derive(message ->
            message == null || message.isEmpty()
                ? ""
                : "<html><div style=\"text-align:center;color:red;\">" + message + "</div></html>"
        )));
        add(errorLabel);
        add(Box.createVerticalStrut(10));

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);
        buttonRow.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
        JButton backButton = new JButton("Go back");
        backButton.addActionListener(e -> viewModel.onBackButtonClicked());
        bindings.add(Bindings.bindEnabled(backButton, viewModel.isSubmitting.derive(b -> !b)));
        buttonRow.add(backButton);
        buttonRow.add(Box.createHorizontalGlue());
        JButton finishButton = new JButton("Finish");
        finishButton.addActionListener(e -> viewModel.onFinishButtonClicked());
        bindings.add(Bindings.bindEnabled(finishButton, viewModel.canFinish));
        buttonRow.add(finishButton);
        add(buttonRow);

        add(Box.createVerticalGlue());

        viewModel.selectedStartMode.addListener(refreshListener);
        viewModel.isExistingAccountSaved.addListener(refreshListener);
        viewModel.isItemLockRequired.addListener(refreshListener);
        refreshCards();
    }

    @Override
    public void close() throws Exception {
        viewModel.selectedStartMode.removeListener(refreshListener);
        viewModel.isExistingAccountSaved.removeListener(refreshListener);
        viewModel.isItemLockRequired.removeListener(refreshListener);
        for (AutoCloseable binding : bindings) {
            binding.close();
        }
        bindings.clear();
    }

    private void refreshCards() {
        StartMode selected = viewModel.selectedStartMode.get();
        boolean isLocked = viewModel.isLockedToExistingAccount();
        newAccountCard.update(selected == StartMode.NEW_ACCOUNT, !isLocked);
        existingAccountCard.update(selected == StartMode.EXISTING_ACCOUNT, !isLocked);
    }

    private JPanel createWarningBox() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(WARNING_BACKGROUND);
        box.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(WARNING_BORDER),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel probe = new JLabel();
        box.add(WrappedText.create(
            "Item locking is not watertight",
            WARNING,
            WARNING_TEXT_WIDTH,
            SwingConstants.LEFT,
            probe.getFont().deriveFont(Font.BOLD)
        ));
        box.add(Box.createVerticalStrut(6));
        box.add(WrappedText.create(
            "Bronzeman Unleashed only sees your bank, inventory and equipment. It cannot check:",
            null, WARNING_TEXT_WIDTH, SwingConstants.LEFT
        ));
        box.add(Box.createVerticalStrut(4));
        for (String bullet : new String[]{
            "Other storage, like the seed vault, POH storage, tool leprechaun and STASH units",
            "Charges in items",
            "Rune pouches, containers you fill from the bank, and your POH servant",
        }) {
            box.add(createBullet(bullet));
            box.add(Box.createVerticalStrut(3));
        }
        box.add(Box.createVerticalStrut(4));
        box.add(WrappedText.create(
            "Some players will find ways around it. Treat it as a helper for honest play, not as anti-cheat.",
            null, WARNING_TEXT_WIDTH, SwingConstants.LEFT
        ));
        box.add(Box.createVerticalStrut(8));

        JCheckBox understandCheckBox = new JCheckBox("I understand");
        understandCheckBox.setOpaque(false);
        understandCheckBox.setIconTextGap(8);
        understandCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        bindings.add(Bindings.bindSelected(understandCheckBox, viewModel.isDisclaimerAccepted));
        box.add(understandCheckBox);

        box.setMaximumSize(new Dimension(CARD_WIDTH, box.getPreferredSize().height));
        return box;
    }

    private static JPanel createBullet(String text) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel dot = new JLabel(new DotIcon());
        dot.setAlignmentY(Component.TOP_ALIGNMENT);
        dot.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 6));
        row.add(dot);
        JTextPane textPane = WrappedText.create(text, MUTED_TEXT, BULLET_TEXT_WIDTH, SwingConstants.LEFT);
        textPane.setAlignmentY(Component.TOP_ALIGNMENT);
        row.add(textPane);
        row.setMaximumSize(new Dimension(WARNING_TEXT_WIDTH, row.getPreferredSize().height));
        return row;
    }

    /** The RuneLite font has no bullet character, so we draw one. */
    private static final class DotIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(WARNING);
            g2.fillOval(x, y, 4, 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 4;
        }

        @Override
        public int getIconHeight() {
            return 4;
        }
    }

    /** A selectable option. The whole card reacts to clicks. */
    private static final class OptionCard extends JPanel {

        private static final Border NORMAL_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(CARD_PADDING + 1, CARD_PADDING + 1, CARD_PADDING + 1, CARD_PADDING + 1)
        );
        private static final Border SELECTED_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SELECTED, 2),
            BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING)
        );

        private final JLabel titleLabel;
        private final RadioIcon radioIcon = new RadioIcon();
        private boolean isEnabled = true;

        OptionCard(String title, String description, String detail, Runnable onSelect) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(CARD_BACKGROUND);
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setBorder(NORMAL_BORDER);

            titleLabel = new JLabel(title, radioIcon, SwingConstants.LEFT);
            titleLabel.setIconTextGap(8);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(titleLabel);
            add(Box.createVerticalStrut(6));
            add(WrappedText.create(description, null, CARD_TEXT_WIDTH, SwingConstants.LEFT));
            add(Box.createVerticalStrut(4));
            add(WrappedText.create(detail, MUTED_TEXT, CARD_TEXT_WIDTH, SwingConstants.LEFT));

            Dimension size = new Dimension(CARD_WIDTH, getPreferredSize().height);
            setPreferredSize(size);
            setMaximumSize(size);

            MouseAdapter clickListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled) {
                        onSelect.run();
                    }
                }
            };
            addClickListener(this, clickListener);
        }

        private static void addClickListener(Component component, MouseAdapter listener) {
            component.addMouseListener(listener);
            if (component instanceof JComponent) {
                for (Component child : ((JComponent) component).getComponents()) {
                    addClickListener(child, listener);
                }
            }
        }

        void update(boolean isSelected, boolean isEnabled) {
            this.isEnabled = isEnabled;
            radioIcon.isSelected = isSelected;
            setBorder(isSelected ? SELECTED_BORDER : NORMAL_BORDER);
            titleLabel.setForeground(isEnabled || isSelected ? Color.WHITE : DISABLED_TEXT);
            Cursor cursor = isEnabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
            setCursorDeep(this, cursor);
            repaint();
        }

        private static void setCursorDeep(Component component, Cursor cursor) {
            component.setCursor(cursor);
            if (component instanceof JComponent) {
                for (Component child : ((JComponent) component).getComponents()) {
                    setCursorDeep(child, cursor);
                }
            }
        }
    }

    private static final class RadioIcon implements Icon {

        private static final int SIZE = 14;
        boolean isSelected;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(isSelected ? SELECTED : MUTED_TEXT);
            g2.drawOval(x + 1, y + 1, SIZE - 3, SIZE - 3);
            if (isSelected) {
                g2.fillOval(x + 4, y + 4, SIZE - 8, SIZE - 8);
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
    }
}
