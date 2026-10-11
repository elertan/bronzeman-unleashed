package com.elertan.panel.components;

import com.elertan.ItemSearchIndex;
import com.elertan.ItemUnlockService;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.UnlockedItem;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Lets a member search all items and unlock one by hand.
 */
@Slf4j
public class ManualUnlockDialog extends JDialog {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_RESULTS = 100;
    private static final int MAX_NOTE_LENGTH = 100;
    private static final int SEARCH_DEBOUNCE_MS = 150;

    private final ItemUnlockService itemUnlockService;
    private final UnlockedItemsDataProvider unlockedItemsDataProvider;
    private final ItemManager itemManager;
    private final ClientThread clientThread;

    private final Map<Integer, AsyncBufferedImage> iconCache = new HashMap<>();
    private final Set<Integer> iconRepaintHooks = new HashSet<>();
    private final IconTextField searchField = new IconTextField();
    private final JList<ItemSearchIndex.Entry> resultList = new JList<>();
    private final JLabel statusLabel = new JLabel();
    private final JLabel selectedLabel = new JLabel();
    private final JTextField noteField = new JTextField();
    private final JButton unlockButton = new JButton("Unlock");
    private final Timer searchDebounceTimer;
    private List<ItemSearchIndex.Entry> entries;
    private Set<Integer> unlockedItemIds = Collections.emptySet();

    private ManualUnlockDialog(
        Window owner,
        ItemSearchIndex itemSearchIndex,
        ItemUnlockService itemUnlockService,
        UnlockedItemsDataProvider unlockedItemsDataProvider,
        ItemManager itemManager,
        ClientThread clientThread
    ) {
        super(owner, "Unlock item", ModalityType.MODELESS);
        this.itemUnlockService = itemUnlockService;
        this.unlockedItemsDataProvider = unlockedItemsDataProvider;
        this.itemManager = itemManager;
        this.clientThread = clientThread;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        setContentPane(content);

        content.add(buildSearchPanel(), BorderLayout.NORTH);
        content.add(buildResultsPanel(), BorderLayout.CENTER);
        content.add(buildUnlockPanel(), BorderLayout.SOUTH);

        searchDebounceTimer = new Timer(SEARCH_DEBOUNCE_MS, e -> updateResults());
        searchDebounceTimer.setRepeats(false);

        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        setMinimumSize(new Dimension(300, 360));
        setSize(new Dimension(340, 460));
        setLocationRelativeTo(owner);

        updateSelection();
        statusLabel.setText("Loading items...");
        searchField.setEditable(false);
        itemSearchIndex.getEntries().whenComplete((loadedEntries, throwable) ->
            Bindings.invokeOnEDT(() -> {
                if (throwable != null) {
                    log.error("Failed to build item search index", throwable);
                    statusLabel.setText("Failed to load items");
                    return;
                }
                entries = loadedEntries;
                searchField.setEditable(true);
                searchField.requestFocusInWindow();
                updateResults();
            })
        );
    }

    @Override
    public void dispose() {
        searchDebounceTimer.stop();
        super.dispose();
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel infoLabel = new JLabel(
            "<html><font color='gray'>Unlock any item by hand. "
                + "Your group will see it as a manual unlock.</font></html>");
        panel.add(infoLabel, BorderLayout.NORTH);

        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setPreferredSize(new Dimension(0, 30));
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDebounceTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDebounceTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDebounceTimer.restart();
            }
        });
        panel.add(searchField, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        resultList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        resultList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        resultList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        resultList.setVisibleRowCount(-1);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer((list, entry, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(44, 40));
            label.setOpaque(isSelected);
            label.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);

            AsyncBufferedImage icon = getCachedIcon(entry.getId());
            // The renderer is invoked repeatedly, so register at most one repaint hook per item id.
            if (iconRepaintHooks.add(entry.getId())) {
                icon.onLoaded(() -> Bindings.invokeOnEDT(resultList::repaint));
            }
            icon.addTo(label);

            boolean isUnlocked = unlockedItemIds.contains(entry.getId());
            // A disabled label paints its icon greyed out
            label.setEnabled(!isUnlocked);
            label.setToolTipText(String.format(
                "<html><b>%s</b>%s</html>",
                escapeHtml(entry.getName()),
                isUnlocked ? "<br><font color='gray'>already unlocked</font>" : ""
            ));
            return label;
        });
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelection();
            }
        });
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    noteField.requestFocusInWindow();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(statusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildUnlockPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        selectedLabel.setFont(FontManager.getRunescapeBoldFont());
        panel.add(selectedLabel, BorderLayout.NORTH);

        ((AbstractDocument) noteField.getDocument()).setDocumentFilter(new MaxLengthFilter(MAX_NOTE_LENGTH));
        noteField.putClientProperty("JTextField.placeholderText", "Reason (optional)");
        noteField.setToolTipText("Shown to your group (max " + MAX_NOTE_LENGTH + " characters)");
        noteField.addActionListener(e -> unlockSelected());
        panel.add(noteField, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        unlockButton.addActionListener(e -> unlockSelected());

        JPanel buttonRow = new JPanel(new BorderLayout(5, 0));
        buttonRow.setOpaque(false);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(unlockButton);
        buttonRow.add(buttons, BorderLayout.EAST);
        panel.add(buttonRow, BorderLayout.SOUTH);

        return panel;
    }

    private void updateResults() {
        if (entries == null) {
            return;
        }

        Map<Integer, UnlockedItem> unlockedItemsMap = unlockedItemsDataProvider.getUnlockedItemsMap();
        unlockedItemIds = unlockedItemsMap == null
            ? Collections.emptySet()
            : new HashSet<>(unlockedItemsMap.keySet());

        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.length() < MIN_QUERY_LENGTH) {
            resultList.setListData(new ItemSearchIndex.Entry[0]);
            statusLabel.setText("Type at least " + MIN_QUERY_LENGTH + " characters to search");
            return;
        }

        // Entries are sorted by name, so names that start with the query come first in name order
        List<ItemSearchIndex.Entry> startsWith = new ArrayList<>();
        List<ItemSearchIndex.Entry> contains = new ArrayList<>();
        for (ItemSearchIndex.Entry entry : entries) {
            String lowerName = entry.getLowerName();
            if (lowerName.startsWith(query)) {
                startsWith.add(entry);
            } else if (lowerName.contains(query)) {
                contains.add(entry);
            }
        }
        List<ItemSearchIndex.Entry> results = new ArrayList<>(startsWith);
        results.addAll(contains);

        int total = results.size();
        if (total > MAX_RESULTS) {
            results = results.subList(0, MAX_RESULTS);
        }
        resultList.setListData(results.toArray(new ItemSearchIndex.Entry[0]));
        resultList.ensureIndexIsVisible(0);

        if (total == 0) {
            statusLabel.setText("No items found");
        } else if (total > MAX_RESULTS) {
            statusLabel.setText(String.format("Showing %d of %d items, refine your search", MAX_RESULTS, total));
        } else {
            statusLabel.setText(total == 1 ? "1 item" : total + " items");
        }
    }

    private void updateSelection() {
        ItemSearchIndex.Entry selected = resultList.getSelectedValue();
        if (selected == null) {
            selectedLabel.setText("Select an item");
            selectedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            unlockButton.setEnabled(false);
            return;
        }

        boolean isUnlocked = unlockedItemIds.contains(selected.getId());
        selectedLabel.setText(isUnlocked ? selected.getName() + " (already unlocked)" : selected.getName());
        selectedLabel.setForeground(isUnlocked ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
        unlockButton.setEnabled(!isUnlocked);
    }

    private void unlockSelected() {
        ItemSearchIndex.Entry selected = resultList.getSelectedValue();
        if (selected == null || !unlockButton.isEnabled()) {
            return;
        }

        int itemId = selected.getId();
        String note = noteField.getText();
        unlockButton.setEnabled(false);
        clientThread.invoke(() -> itemUnlockService.manualUnlockItem(itemId, note)
            .whenComplete((__, throwable) -> Bindings.invokeOnEDT(() -> {
                if (throwable != null) {
                    log.error("Failed to manually unlock item {}", itemId, throwable);
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    JOptionPane.showMessageDialog(
                        this,
                        "Could not unlock '" + selected.getName() + "': " + cause.getMessage(),
                        "Unlock item failed",
                        JOptionPane.ERROR_MESSAGE
                    );
                    updateSelection();
                    return;
                }
                dispose();
            })));
    }

    private AsyncBufferedImage getCachedIcon(int itemId) {
        return iconCache.computeIfAbsent(itemId, itemManager::getImage);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class MaxLengthFilter extends DocumentFilter {

        private final int maxLength;

        private MaxLengthFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
            throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }
            int room = maxLength - (fb.getDocument().getLength() - length);
            if (room <= 0) {
                return;
            }
            super.replace(fb, offset, length, text.length() > room ? text.substring(0, room) : text, attrs);
        }
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        ManualUnlockDialog create(Window owner);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private ItemSearchIndex itemSearchIndex;
        @Inject
        private ItemUnlockService itemUnlockService;
        @Inject
        private UnlockedItemsDataProvider unlockedItemsDataProvider;
        @Inject
        private ItemManager itemManager;
        @Inject
        private ClientThread clientThread;

        @Override
        public ManualUnlockDialog create(Window owner) {
            return new ManualUnlockDialog(
                owner,
                itemSearchIndex,
                itemUnlockService,
                unlockedItemsDataProvider,
                itemManager,
                clientThread
            );
        }
    }
}
