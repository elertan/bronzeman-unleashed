package com.elertan.panel.screens.setup;

import com.elertan.models.StartMode;
import com.elertan.panel.BUPanel;
import com.elertan.panel.components.StatusIcon;
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
import net.runelite.client.ui.FontManager;

/**
 * Setup step "Your account": new or existing account.
 */
public class AccountTypeStepView extends JPanel implements AutoCloseable {

    private static final int CONTENT_WIDTH = BUPanel.PANEL_WIDTH - 12;
    private static final int CARD_WIDTH = CONTENT_WIDTH - 2;
    private static final int CARD_PADDING = 12;
    private static final int CARD_TEXT_WIDTH = CARD_WIDTH - 2 * CARD_PADDING - 4;
    private static final int WARNING_TEXT_WIDTH = CARD_WIDTH - 2 - 2 * 10;
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
            "RECOMMENDED",
            "Everything you own counts as unlocked.",
            "Classic Bronzeman, for an account that starts from nothing.",
            () -> viewModel.onStartModeSelected(StartMode.NEW_ACCOUNT)
        );
        existingAccountCard = new OptionCard(
            "Existing account",
            null,
            "Items you already own are locked in your bank.",
            "Only what you get from now on unlocks and can be used.",
            () -> viewModel.onStartModeSelected(StartMode.EXISTING_ACCOUNT)
        );
        add(newAccountCard);
        add(Box.createVerticalStrut(8));
        add(existingAccountCard);
        add(Box.createVerticalStrut(8));

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
        refreshCards();
    }

    @Override
    public void close() throws Exception {
        viewModel.selectedStartMode.removeListener(refreshListener);
        viewModel.isExistingAccountSaved.removeListener(refreshListener);
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
            "Before you choose",
            WARNING,
            WARNING_TEXT_WIDTH,
            SwingConstants.LEFT,
            probe.getFont().deriveFont(Font.BOLD)
        ));
        for (String paragraph : new String[]{
            "Your bank is counted once, and everything in it stays locked. Only what you get from now on "
                + "unlocks, and only that part of each stack can be withdrawn.",
            "Bronzeman Unleashed sees your bank, inventory and equipment, so these work best. It can't fully "
                + "follow other storage (like the seed vault or POH), charges in items, or containers you fill "
                + "from the bank, such as rune pouches. Some items may slip through and unlock.",
            "For the full Bronzeman experience, start a new account.",
        }) {
            box.add(Box.createVerticalStrut(6));
            box.add(WrappedText.create(paragraph, null, WARNING_TEXT_WIDTH, SwingConstants.LEFT));
        }
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

        OptionCard(String title, String badge, String description, String detail, Runnable onSelect) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(CARD_BACKGROUND);
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setBorder(NORMAL_BORDER);

            titleLabel = new JLabel(title, radioIcon, SwingConstants.LEFT);
            titleLabel.setIconTextGap(8);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
            JPanel titleRow = new JPanel();
            titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
            titleRow.setOpaque(false);
            titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleRow.add(titleLabel);
            titleRow.add(Box.createHorizontalGlue());
            if (badge != null) {
                titleRow.add(createBadge(badge));
            }
            add(titleRow);
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

        private static JLabel createBadge(String text) {
            JLabel badge = new JLabel(text);
            badge.setFont(FontManager.getRunescapeSmallFont());
            badge.setForeground(StatusIcon.DONE_COLOR);
            return badge;
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
