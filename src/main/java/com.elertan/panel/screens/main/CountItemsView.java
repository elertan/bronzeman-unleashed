package com.elertan.panel.screens.main;

import com.elertan.ItemLockService.Checklist;
import com.elertan.panel.BUPanel;
import com.elertan.panel.components.ChecklistRow;
import com.elertan.panel.components.ProgressBar;
import com.elertan.panel.components.StatusIcon;
import com.elertan.panel.components.WrappedText;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * The count card for an existing account that is not counted yet.
 */
public class CountItemsView extends JPanel implements AutoCloseable {

    private static final int CONTENT_WIDTH = BUPanel.PANEL_WIDTH - 20;
    private static final int ROW_WIDTH = CONTENT_WIDTH - 2 * 10 - 2;
    private static final Color CARD_BACKGROUND = new Color(39, 39, 39);
    private static final Color CARD_BORDER = new Color(58, 58, 58);
    private static final Color MUTED_TEXT = new Color(145, 145, 145);
    private static final Color NOTE_BACKGROUND = new Color(32, 32, 32);

    private final CountItemsViewModel viewModel;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private final ChecklistRow bankRow = new ChecklistRow(ROW_WIDTH);
    private final ChecklistRow inventoryRow = new ChecklistRow(ROW_WIDTH);
    private final ChecklistRow equipmentRow = new ChecklistRow(ROW_WIDTH);
    private final ChecklistRow geRow = new ChecklistRow(ROW_WIDTH);
    private final JLabel progressLabel = new JLabel();
    private final ProgressBar progressBar = new ProgressBar(CONTENT_WIDTH, 4);
    private final PropertyChangeListener checklistListener = e -> Bindings.invokeOnEDT(this::refresh);

    public CountItemsView(CountItemsViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));

        JLabel titleLabel = new JLabel("Count your items");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(6));
        add(WrappedText.create(
            "One last step. Bank everything, then count. Everything you own now gets locked, "
                + "and only what you get after that counts.",
            MUTED_TEXT,
            CONTENT_WIDTH,
            SwingConstants.LEFT
        ));
        add(Box.createVerticalStrut(14));

        JPanel progressRow = new JPanel(new BorderLayout());
        progressRow.setOpaque(false);
        progressRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressRow.setMaximumSize(new Dimension(CONTENT_WIDTH, 20));
        progressLabel.setForeground(MUTED_TEXT);
        progressRow.add(progressLabel, BorderLayout.WEST);
        add(progressRow);
        add(Box.createVerticalStrut(4));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(progressBar);
        add(Box.createVerticalStrut(10));

        JPanel checklistCard = new JPanel();
        checklistCard.setLayout(new BoxLayout(checklistCard, BoxLayout.Y_AXIS));
        checklistCard.setBackground(CARD_BACKGROUND);
        checklistCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklistCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        checklistCard.add(bankRow);
        checklistCard.add(inventoryRow);
        checklistCard.add(equipmentRow);
        checklistCard.add(geRow);
        add(checklistCard);
        add(Box.createVerticalStrut(14));

        JButton countButton = new JButton("Count my items");
        countButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        countButton.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        countButton.setMaximumSize(new Dimension(CONTENT_WIDTH, countButton.getPreferredSize().height));
        countButton.addActionListener(e -> viewModel.onCountClicked());
        bindings.add(Bindings.bindEnabled(countButton, Property.deriveMany(
            Arrays.asList(viewModel.checklist, viewModel.isSubmitting),
            values -> {
                Checklist checklist = (Checklist) values.get(0);
                return checklist != null && checklist.isComplete() && !Boolean.TRUE.equals(values.get(1));
            }
        )));
        add(countButton);
        add(Box.createVerticalStrut(6));

        JLabel errorLabel = new JLabel();
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bindings.add(Bindings.bindLabelText(errorLabel, viewModel.errorMessage.derive(message ->
            message == null || message.isEmpty()
                ? ""
                : "<html><div style=\"width:" + CONTENT_WIDTH + "px;color:#ff6b6b;\">" + message + "</div></html>"
        )));
        add(errorLabel);
        add(Box.createVerticalStrut(8));

        JPanel note = new JPanel();
        note.setLayout(new BoxLayout(note, BoxLayout.Y_AXIS));
        note.setBackground(NOTE_BACKGROUND);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        note.add(WrappedText.create(
            "Unlocks are paused until you count. Anything you get before that is locked too.",
            MUTED_TEXT,
            CONTENT_WIDTH - 2 - 16,
            SwingConstants.LEFT
        ));
        note.setMaximumSize(new Dimension(CONTENT_WIDTH, note.getPreferredSize().height));
        add(note);
        add(Box.createVerticalStrut(12));

        JLabel settingsLink = new JLabel("<html><u>Settings</u></html>");
        settingsLink.setForeground(MUTED_TEXT);
        settingsLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                viewModel.onSettingsClicked();
            }
        });
        add(settingsLink);

        add(Box.createVerticalGlue());

        viewModel.checklist.addListener(checklistListener);
        refresh();
    }

    @Override
    public void close() throws Exception {
        viewModel.checklist.removeListener(checklistListener);
        for (AutoCloseable binding : bindings) {
            binding.close();
        }
        bindings.clear();
    }

    private void refresh() {
        Checklist checklist = viewModel.checklist.get();
        if (checklist == null) {
            return;
        }
        int done = 0;

        bankRow.update(checklist.isBankOpen() ? StatusIcon.Status.DONE : StatusIcon.Status.TODO,
            checklist.isBankOpen() ? "Bank is open" : "Open your bank",
            "The plugin counts your bank while it is open.");
        inventoryRow.update(checklist.isInventoryEmpty() ? StatusIcon.Status.DONE : StatusIcon.Status.TODO,
            checklist.isInventoryEmpty() ? "Inventory is empty" : "Bank your inventory",
            "Everything you carry must be in your bank.");
        equipmentRow.update(checklist.isEquipmentEmpty() ? StatusIcon.Status.DONE : StatusIcon.Status.TODO,
            checklist.isEquipmentEmpty() ? "Nothing equipped" : "Bank your equipment",
            "Everything you wear must be in your bank.");
        Boolean noGeOffers = checklist.getNoGeOffers();
        if (noGeOffers == null) {
            geRow.update(StatusIcon.Status.WAITING, "Checking Grand Exchange…", null);
        } else {
            geRow.update(noGeOffers ? StatusIcon.Status.DONE : StatusIcon.Status.TODO,
                noGeOffers ? "No Grand Exchange offers" : "Clear your GE offers",
                "Cancel or collect them. Items in offers cannot be counted.");
        }

        done += checklist.isBankOpen() ? 1 : 0;
        done += checklist.isInventoryEmpty() ? 1 : 0;
        done += checklist.isEquipmentEmpty() ? 1 : 0;
        done += Boolean.TRUE.equals(noGeOffers) ? 1 : 0;
        progressLabel.setText(done == 4 ? "Ready to count" : done + " of 4 ready");
        progressLabel.setForeground(done == 4 ? StatusIcon.DONE_COLOR : MUTED_TEXT);
        progressBar.setProgress(done / 4f);
        revalidate();
        repaint();
    }
}
