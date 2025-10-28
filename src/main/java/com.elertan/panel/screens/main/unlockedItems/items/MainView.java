package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.BUResourceService;
import com.elertan.MemberService;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainView extends JPanel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainView create(MainViewViewModel viewModel);
    }

    private static final class FactoryImpl implements Factory {
        @Inject
        private BUResourceService buResourceService;
        @Inject
        private ItemManager itemManager;
        @Inject
        private MemberService memberService;

        @Override
        public MainView create(MainViewViewModel viewModel) {
            return new MainView(viewModel, buResourceService, itemManager, memberService);
        }
    }

    private final MainViewViewModel viewModel;
    private final BUResourceService buResourceService;
    private final ItemManager itemManager;
    private final MemberService memberService;

    private final AutoCloseable cardLayoutBinding;
    private AutoCloseable listBinding;

    private MainView(MainViewViewModel viewModel, BUResourceService buResourceService, ItemManager itemManager, MemberService memberService) {
        this.viewModel = viewModel;
        this.buResourceService = buResourceService;
        this.itemManager = itemManager;
        this.memberService = memberService;

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);
        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.viewState, this::buildViewState);
    }

    @Override
    public void close() throws Exception {
        if (listBinding != null) {
            listBinding.close();
        }
        cardLayoutBinding.close();
    }

    private JPanel buildViewState(MainViewViewModel.ViewState viewState) {
        switch (viewState) {
            case LOADING:
                return buildLoadingViewState();
            case EMPTY:
                return buildEmptyViewState();
            case READY:
                return buildReadyViewState();
        }

        throw new IllegalStateException("Unknown view state: " + viewState);
    }

    private JPanel buildLoadingViewState() {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel();
        label.setIcon(buResourceService.getLoadingSpinnerImageIcon());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    private JPanel buildEmptyViewState() {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel("Nothing unlocked yet");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    private static class ListItem {
        @Getter
        private final UnlockedItem item;
        @Getter
        private final Member acquiredByMember;
        @Getter
        private final AsyncBufferedImage icon;

        private ListItem(UnlockedItem item, Member acquiredByMember, AsyncBufferedImage icon) {
            this.item = item;
            this.acquiredByMember = acquiredByMember;
            this.icon = icon;
        }
    }

    private JPanel buildReadyViewState() {
        JPanel panel = new JPanel(new BorderLayout());

        JList<ListItem> list = new JList<>();
        list.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        list.setBackground(ColorScheme.DARK_GRAY_COLOR);

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);

        list.setCellRenderer((jl, listItem, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            label.setHorizontalAlignment(SwingConstants.CENTER);

            AsyncBufferedImage icon = listItem.getIcon();
            icon.addTo(label);

            // repaint when image loads
            icon.onLoaded(jl::repaint);

            UnlockedItem item = listItem.getItem();
            // rich tooltip
            String name = item.getName();
            Member acquiredByMember = listItem.getAcquiredByMember();
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

        Property<List<ListItem>> listItems = viewModel.unlockedItems.derive((unlockedItems) -> {
            if (unlockedItems == null) {
                return null;
            }

            return unlockedItems
                    .stream()
                    .map((unlockedItem) -> {
                        Member acquiredByMember = null;
                        try {
                            acquiredByMember = memberService.getMemberByAccountHash(unlockedItem.getAcquiredByAccountHash());
                        } catch (Exception e) {
                            // ignored
                        }
                        AsyncBufferedImage icon = getCachedIcon(unlockedItem.getId());
                        return new ListItem(unlockedItem, acquiredByMember, icon);
                    })
                    .collect(Collectors.toList());
        });

        Consumer<List<ListItem>> setter = (newListItems) -> {
            if (newListItems == null) {
                list.setListData(new ListItem[0]);
                return;
            }
            list.setListData(newListItems.toArray(new ListItem[0]));
        };

        PropertyChangeListener listItemsListener = (event) -> {
            @SuppressWarnings("unchecked")
            List<ListItem> newListItems = (List<ListItem>)event.getNewValue();
            setter.accept(newListItems);
        };
        listItems.addListener(listItemsListener);
        setter.accept(listItems.get());

        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        listBinding = () -> listItems.removeListener(listItemsListener);

        return panel;
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

    private final Map<Integer, AsyncBufferedImage> iconCache = new HashMap<>();
    private AsyncBufferedImage getCachedIcon(int id) {
        return iconCache.computeIfAbsent(id, itemManager::getImage);
    }

    private Timer relativeTimeTimer;
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
