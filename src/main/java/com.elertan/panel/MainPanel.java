package com.elertan.panel;

import com.elertan.BUResourceService;
import com.elertan.data.MembersDataProvider;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class MainPanel extends JPanel implements AutoCloseable {
    private enum View {
        Loading,
        Items
    }

    private static final String UNLOCKED_BY_EVERYONE_OPTION = "Everyone";

    private static final String SORT_OPTION_UNLOCKED_AT_ASC = "Unlocked at (asc)";
    private static final String SORT_OPTION_ALPHABETICAL_ASC = "Alphabetical (asc)";
    private static final String SORT_OPTION_PLAYER_ASC = "Player (asc)";
    private static final String SORT_OPTION_UNLOCKED_AT_DESC = "Unlocked at (desc)";
    private static final String SORT_OPTION_ALPHABETICAL_DESC = "Alphabetical (desc)";
    private static final String SORT_OPTION_PLAYER_DESC = "Player (desc)";

    private static final Set<String> SORT_OPTIONS = new HashSet<>(Arrays.asList(
            SORT_OPTION_UNLOCKED_AT_ASC, SORT_OPTION_ALPHABETICAL_ASC, SORT_OPTION_PLAYER_ASC,
            SORT_OPTION_UNLOCKED_AT_DESC, SORT_OPTION_ALPHABETICAL_DESC, SORT_OPTION_PLAYER_DESC
    ));

    private final BUResourceService buResourceService;
    private final ItemManager itemManager;
    private final UnlockedItemsDataProvider unlockedItemsDataProvider;
    private final MembersDataProvider membersDataProvider;

    private final Consumer<UnlockedItemsDataProvider.State> unlockedItemDataProviderStateListener = this::unlockedItemDataProviderStateListener;
    private final Consumer<MembersDataProvider.State> membersDataProviderStateListener = this::membersDataProviderStateListener;

    private final Map<Integer, AsyncBufferedImage> iconCache = new HashMap<>();

    private final Set<Integer> excludedItems = ImmutableSet.of(
            ItemID.OSRS_BOND,
            ItemID.COINS,
            ItemID.COINS_1,
            ItemID.COINS_2,
            ItemID.COINS_3,
            ItemID.COINS_4,
            ItemID.COINS_5,
            ItemID.COINS_25,
            ItemID.COINS_100,
            ItemID.COINS_250,
            ItemID.COINS_1000,
            ItemID.COINS_10000
    );

    // persistent UI controls
    private final IconTextField searchbar = new IconTextField();
    private final JComboBox<String> sortedByBox = new JComboBox<String>(SORT_OPTIONS.toArray(new String[0])) {{
        setSelectedItem(SORT_OPTION_UNLOCKED_AT_DESC);
    }};
    private final JComboBox<String> unlockedByBox = new JComboBox<>(new String[]{UNLOCKED_BY_EVERYONE_OPTION});

    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel loadingView = new JPanel(new GridBagLayout());
    private final JPanel itemsView = new JPanel(new BorderLayout());
    private final JPanel extraFilterAndSortPanel = new JPanel(new BorderLayout());
    private final JPanel contentPanel = new JPanel(new BorderLayout()); // holds the item grid

    private UnlockedItemsDataProvider.UnlockedItemsMapListener unlockedItemsListener;

    private String searchTerm = "";
    private String unlockedBy = null;
    private String sortedBy = SORT_OPTION_UNLOCKED_AT_DESC;
    private Timer debounceTimer;
    private Timer relativeTimeTimer;


    public MainPanel(BUResourceService buResourceService, ItemManager itemManager, UnlockedItemsDataProvider unlockedItemsDataProvider, MembersDataProvider membersDataProvider) {
        this.buResourceService = buResourceService;
        this.itemManager = itemManager;
        this.unlockedItemsDataProvider = unlockedItemsDataProvider;
        this.membersDataProvider = membersDataProvider;

        setLayout(new BorderLayout());

        buildLoadingView();
        buildItemsView();

        extraFilterAndSortPanel.setLayout(new BoxLayout(extraFilterAndSortPanel, BoxLayout.Y_AXIS));
        extraFilterAndSortPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        cards.add(loadingView, View.Loading.name());
        cards.add(itemsView, View.Items.name());

        add(cards, BorderLayout.CENTER);

        // listen for search text changes
        searchbar.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                searchTerm = searchbar.getText().trim().toLowerCase();
                debounceRefresh();
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

        sortedByBox.addActionListener(event -> {
            final String selected = (String) sortedByBox.getSelectedItem();
            if (!Objects.equals(sortedBy, selected)) {
                sortedBy = selected;
                refresh();
            }
        });

        unlockedByBox.addActionListener(event -> {
            String newUnlockedBy = null;

            final String selected = (String) unlockedByBox.getSelectedItem();
            if (selected != null && !selected.equals(UNLOCKED_BY_EVERYONE_OPTION)) {
                newUnlockedBy = selected;
            }
            if (!Objects.equals(newUnlockedBy, unlockedBy)) {
                unlockedBy = newUnlockedBy;
                refresh();
            }
        });

        unlockedItemsListener = new UnlockedItemsDataProvider.UnlockedItemsMapListener() {
            @Override
            public void onUpdate(UnlockedItem unlockedItem) {
                log.info("MainPanel: unlockedItemsListener on update REFRESH");
                refresh();
            }

            @Override
            public void onDelete(int itemId) {
                log.info("MainPanel: unlockedItemsListener on delete REFRESH");
                refresh();
            }
        };
        unlockedItemsDataProvider.addUnlockedItemsMapListener(unlockedItemsListener);
        unlockedItemsDataProvider.addStateListener(unlockedItemDataProviderStateListener);
        membersDataProvider.addStateListener(membersDataProviderStateListener);

        membersDataProvider.waitUntilReady(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("error waiting for members data provider to become ready", throwable);
                return;
            }
            log.info("MainPanel: membersDataProvider wait until ready REFRESH");
            refresh();
        });

        unlockedItemsDataProvider
                .waitUntilReady(null)
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.error("error waiting for unlocked item data provider to become ready", throwable);
                        return;
                    }
                    log.info("MainPanel: unlockedItemsDataProvider wait until ready REFRESH");
                    refresh();
                });


        refresh();
    }

    @Override
    public void close() throws Exception {
        membersDataProvider.removeStateListener(membersDataProviderStateListener);
        unlockedItemsDataProvider.removeStateListener(unlockedItemDataProviderStateListener);
        unlockedItemsDataProvider.removeUnlockedItemsMapListener(unlockedItemsListener);
    }

    private void unlockedItemDataProviderStateListener(UnlockedItemsDataProvider.State state) {
        log.info("MainPanel: unlockedItemDataProviderStateListener REFRESH");
        refresh();
    }

    private void membersDataProviderStateListener(MembersDataProvider.State state) {
        log.info("MainPanel: membersDataProviderStateListener REFRESH");
        refresh();
    }

    private void buildLoadingView() {
        loadingView.setLayout(new BorderLayout());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        JLabel titleLabel = new JLabel("Bronzeman Unleashed");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(titleLabel);

        inner.add(Box.createVerticalStrut(15));

        JLabel loadingSpinnerLabel = new JLabel();
        loadingSpinnerLabel.setIcon(buResourceService.getLoadingSpinnerImageIcon());
        loadingSpinnerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(loadingSpinnerLabel);

        loadingView.add(inner, BorderLayout.NORTH);
    }

    private void buildItemsView() {
        JPanel searchbarPanel = new JPanel();
        searchbarPanel.setLayout(new BoxLayout(searchbarPanel, BoxLayout.Y_AXIS));
        searchbarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        // search bar
        searchbar.setIcon(IconTextField.Icon.SEARCH);
        searchbar.setPreferredSize(new Dimension(BUPanel.PANEL_WIDTH - 20, 30));
        searchbar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchbar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchbar.setMinimumSize(new Dimension(0, 30));

        // Create config button
        JButton configButton = new JButton();
        configButton.setIcon(new ImageIcon(buResourceService.getConfigureIconBufferedImage()));
        configButton.setToolTipText("Open configuration");
        configButton.setPreferredSize(new Dimension(30, 30));
        configButton.setFocusable(false);
        configButton.setBorderPainted(false);
        configButton.setContentAreaFilled(false);
        configButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        configButton.addActionListener(e -> {

        });

        // Layout beside searchbar
        JPanel searchbarHeader = new JPanel();
        searchbarHeader.setLayout(new BorderLayout(5, 0));
        searchbarHeader.add(searchbar, BorderLayout.CENTER);
        searchbarHeader.add(configButton, BorderLayout.EAST);

        searchbarPanel.add(searchbarHeader);

        itemsView.add(searchbarPanel, BorderLayout.NORTH);

        JPanel bottomView = new JPanel(new BorderLayout());
        bottomView.add(extraFilterAndSortPanel, BorderLayout.NORTH);
        bottomView.add(contentPanel, BorderLayout.CENTER);

        itemsView.add(bottomView, BorderLayout.CENTER);
    }

    private class ComputedData {
        @Getter
        private final List<UnlockedItem> items;
        @Getter
        private final Set<String> allAcquiredByNames;

        public ComputedData(List<UnlockedItem> items, Set<String> allAcquiredByNames) {
            this.items = items;
            this.allAcquiredByNames = allAcquiredByNames;
        }
    }

    public void refresh() {
        CompletableFuture.supplyAsync(() -> {
                    if (unlockedItemsDataProvider.getState() == UnlockedItemsDataProvider.State.NotReady) {
                        return null;
                    }
                    Map<Integer, UnlockedItem> itemMap = unlockedItemsDataProvider.getUnlockedItemsMap();
                    if (itemMap == null) {
                        return null;
                    }

                    Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
                    Set<String> allAcquiredByNames = membersMap.values().stream()
                            .map(Member::getName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(TreeSet::new));

                    final List<UnlockedItem> items = itemMap.values().stream()
                            .filter(item -> {
                                final int itemId = item.getId();
                                if (excludedItems.contains(itemId)) {
                                    return false;
                                }

                                if (unlockedBy != null) {
                                    long acquiredByAccountHash = item.getAcquiredByAccountHash();
                                    Member member = membersMap.get(acquiredByAccountHash);
                                    if (member == null || !member.getName().equals(unlockedBy)) {
                                        return false;
                                    }
                                }

                                if (searchTerm.isEmpty()) {
                                    return true;
                                }

                                final String itemName = item.getName();
                                final String lowerCaseItemName = itemName.toLowerCase();
                                return lowerCaseItemName.contains(searchTerm);
                            })
                            .sorted((left, right) -> {
                                int value = 0;

                                switch (sortedBy) {
                                    case SORT_OPTION_UNLOCKED_AT_ASC:
                                        value = left.getAcquiredAt().getValue().compareTo(right.getAcquiredAt().getValue());
                                        break;
                                    case SORT_OPTION_ALPHABETICAL_ASC:
                                        value = left.getName().compareToIgnoreCase(right.getName());
                                        break;
                                    case SORT_OPTION_PLAYER_ASC: {
                                        Member leftMember = membersMap.get(left.getAcquiredByAccountHash());
                                        Member rightMember = membersMap.get(right.getAcquiredByAccountHash());
                                        if (leftMember != null && rightMember != null) {
                                            value = leftMember.getName().compareToIgnoreCase(rightMember.getName());
                                        } else if (leftMember == null) {
                                            value = 1;
                                        } else {
                                            value = -1;
                                        }
                                        break;
                                    }
                                    case SORT_OPTION_UNLOCKED_AT_DESC:
                                        value = right.getAcquiredAt().getValue().compareTo(left.getAcquiredAt().getValue());
                                        break;
                                    case SORT_OPTION_ALPHABETICAL_DESC:
                                        value = right.getName().compareToIgnoreCase(left.getName());
                                        break;
                                    case SORT_OPTION_PLAYER_DESC: {
                                        Member leftMember = membersMap.get(left.getAcquiredByAccountHash());
                                        Member rightMember = membersMap.get(right.getAcquiredByAccountHash());
                                        if (leftMember != null && rightMember != null) {
                                            value = rightMember.getName().compareToIgnoreCase(leftMember.getName());
                                        } else if (leftMember == null) {
                                            value = -1;
                                        } else {
                                            value = 1;
                                        }
                                        break;
                                    }
                                }
                                if (value == 0) {
                                    // Default to unlocked at descending for consistency when items are equal
                                    return right.getAcquiredAt().getValue().compareTo(left.getAcquiredAt().getValue());
                                }
                                return value;
                            })
                            .collect(Collectors.toList());

                    return new ComputedData(items, allAcquiredByNames);
                })
                .handle((data, throwable) -> {
                    if (throwable != null) {
                        log.error("error computing data", throwable);
                        return null;
                    }
                    return data;
                })
                .thenAccept(data -> SwingUtilities.invokeLater(() -> buildUI(data)));
    }

    private void debounceRefresh() {
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
        debounceTimer = new Timer(150, e -> refresh());
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void buildUI(ComputedData data) {
        CardLayout cl = (CardLayout) cards.getLayout();
        if (data == null) {
            cl.show(cards, View.Loading.name());
            return;
        }
        final List<UnlockedItem> items = data.getItems();
        final Set<String> allAcquiredByNames = data.getAllAcquiredByNames();

        if (items == null || allAcquiredByNames == null) {
            cl.show(cards, View.Loading.name());
            return;
        }

        buildExtraFilterAndSortView(allAcquiredByNames);
        buildItemView(items);
        cl.show(cards, View.Items.name());
    }

    private void buildExtraFilterAndSortView(Set<String> allAcquiredByNames) {
        extraFilterAndSortPanel.removeAll();

        // sort row
        JPanel sortRow = new JPanel(new BorderLayout(5, 0));
        sortRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel sortLabel = new JLabel("Sort by:");
        sortLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortedByBox, BorderLayout.CENTER);
        extraFilterAndSortPanel.add(sortRow);


        // filter row
        JPanel filterRow = new JPanel(new BorderLayout(5, 0));
        filterRow.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        JLabel unlockedByLabel = new JLabel("Unlocked by:");
        unlockedByLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        buildUnlockedByCombobox(allAcquiredByNames);

        filterRow.add(unlockedByLabel, BorderLayout.WEST);
        filterRow.add(unlockedByBox, BorderLayout.CENTER);
        extraFilterAndSortPanel.add(filterRow);
    }

    private void buildUnlockedByCombobox(Set<String> allAcquiredByNames) {
        // The everyone item is always there

        final boolean isCurrentUnlockedByStillInList = unlockedBy == null || allAcquiredByNames.contains(unlockedBy);
        if (!isCurrentUnlockedByStillInList) {
            // If the current unlocker is no longer in the list, we need to go back to everyone as a reset
            unlockedByBox.setSelectedItem(UNLOCKED_BY_EVERYONE_OPTION);
        }

        final Set<String> currentItems = new HashSet<>();

        // Remove all items that are not in the list, or is the everyone option
        for (int i = unlockedByBox.getItemCount() - 1; i >= 0; i--) {
            String itemName = unlockedByBox.getItemAt(i);
            if (!allAcquiredByNames.contains(itemName) && !itemName.equals(UNLOCKED_BY_EVERYONE_OPTION)) {
                unlockedByBox.removeItemAt(i);
            } else {
                currentItems.add(itemName);
            }
        }

        // Add all new items
        for (String itemName : allAcquiredByNames) {
            if (!currentItems.contains(itemName)) {
                unlockedByBox.addItem(itemName);
            }
        }
    }

    private void buildItemView(List<UnlockedItem> items) {
        contentPanel.removeAll();

        if (items.isEmpty()) {
            JPanel centerPanel = new JPanel(new GridBagLayout());
            JLabel noItems = new JLabel("No items");
            noItems.setHorizontalAlignment(SwingConstants.CENTER);
            centerPanel.add(noItems);
            contentPanel.add(centerPanel, BorderLayout.CENTER);
        } else {
            final JList<UnlockedItem> list = new JList<>(new Vector<>(items));

            Map<Long, Member> membersMap = membersDataProvider.getMembersMap();

            list.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            list.setBackground(ColorScheme.DARK_GRAY_COLOR);

            list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            list.setVisibleRowCount(-1);

            list.setCellRenderer((jl, item, index, isSelected, cellHasFocus) -> {
                final JLabel label = new JLabel();
                label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                label.setHorizontalAlignment(SwingConstants.CENTER);

                AsyncBufferedImage icon = getCachedIcon(item.getId());
                icon.addTo(label);

                // repaint when image loads
                icon.onLoaded(jl::repaint);

                // rich tooltip
                String name = item.getName();
                Member acquiredByMember = membersMap.get(item.getAcquiredByAccountHash());
                String acquiredBy = acquiredByMember == null ? "Unknown" : acquiredByMember.getName();
                OffsetDateTime now = OffsetDateTime.now();
                String acquiredAt = item.getAcquiredAt() != null ? formatRelativeTime(now, item.getAcquiredAt().getValue()) : "Unknown";
                label.setToolTipText(String.format(
                        "<html>"
                                + "<b>%s</b><br>"
                                + "<p><font color='gray'>by </font>%s</p>"
                                + "<font color='gray'>%s</font>"
                                + "</html>",
                        name, acquiredBy, acquiredAt
                ));

                return label;
            });

            scheduleRelativeTimeUpdate(list);

            contentPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private AsyncBufferedImage getCachedIcon(int id) {
        return iconCache.computeIfAbsent(id, itemManager::getImage);
    }

    private static String formatRelativeTime(OffsetDateTime now, OffsetDateTime time) {
        if (time == null) return "Unknown";

        Duration d = Duration.between(time, now);
        long seconds = d.getSeconds();

        if (seconds < 60) return "Just now";
        if (seconds < 3600) {
            int minutes = (int) (seconds / 60);
            if (minutes == 1) {
                return "1 minute ago";
            }
            return minutes + " minutes ago";
        }
        if (seconds < 86400) {
            int hours = (int) (seconds / 3600);
            if (hours == 1) {
                return "1 hour ago";
            }
            return hours + " hours ago";
        }
        if (seconds < 172800) return "Yesterday at " + time.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (seconds < 604800) {
            String dayOfWeek = time.format(DateTimeFormatter.ofPattern("EEEE"));
            return dayOfWeek + " at " + time.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private void scheduleRelativeTimeUpdate(JList<?> list) {
        // cancel previous timer if active
        if (relativeTimeTimer != null && relativeTimeTimer.isRunning()) {
            relativeTimeTimer.stop();
        }

        // start a new 1-minute timer
        relativeTimeTimer = new Timer(60_000, e -> {
            list.repaint();               // update visible relative times
            scheduleRelativeTimeUpdate(list); // restart countdown
        });
        relativeTimeTimer.setRepeats(false);
        relativeTimeTimer.start();
    }
}
