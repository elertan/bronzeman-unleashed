package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.BUResourceService;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.ui.Bindings;
import com.elertan.utils.OffsetDateTimeUtils;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
public class MainView extends JPanel implements AutoCloseable {

    private final MainViewViewModel viewModel;
    private final BUResourceService buResourceService;
    private final AutoCloseable cardLayoutBinding;
    private AutoCloseable listBinding;
    private Timer relativeTimeUpdateTimer;

    private MainView(MainViewViewModel viewModel, BUResourceService buResourceService) {
        this.viewModel = viewModel;
        this.buResourceService = buResourceService;

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);
        cardLayoutBinding = Bindings.bindCardLayout(
            this,
            cardLayout,
            viewModel.viewState,
            this::buildViewState
        );
    }

    @Override
    public void close() throws Exception {
        if (relativeTimeUpdateTimer != null && relativeTimeUpdateTimer.isRunning()) {
            relativeTimeUpdateTimer.stop();
        }
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

    private JPanel buildReadyViewState() {
        JPanel panel = new JPanel(new BorderLayout());
        Set<Integer> iconRepaintHooks = new HashSet<>();

        JList<MainViewViewModel.ListItem> list = new JList<>();
        list.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        list.setBackground(ColorScheme.DARK_GRAY_COLOR);

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);

        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem removeFromUnlockedItemsMenuItem = new JMenuItem("Remove from unlocked items");
        contextMenu.add(removeFromUnlockedItemsMenuItem);

        removeFromUnlockedItemsMenuItem.addActionListener(e -> {
            MainViewViewModel.ListItem selected = list.getSelectedValue();
            if (selected == null) {
                return;
            }

            viewModel.removeFromUnlockedItems(selected);
        });

        // Show the popup on right click and select the item under the cursor
        list.addMouseListener(new MouseAdapter() {
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index != -1) {
                        list.setSelectedIndex(index);
                        contextMenu.show(list, e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
        });

        list.setCellRenderer((jl, listItem, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            label.setHorizontalAlignment(SwingConstants.CENTER);

            AsyncBufferedImage icon = listItem.getIcon();
            int itemId = listItem.getItem().getId();
            if (iconRepaintHooks.add(itemId)) {
                icon.onLoaded(() -> Bindings.invokeOnEDT(list::repaint));
            }
            icon.addTo(label);

            UnlockedItem item = listItem.getItem();
            // rich tooltip
            String name = item.getName();
            Member acquiredByMember = listItem.getAcquiredByMember();
            String acquiredBy = acquiredByMember == null ? null : acquiredByMember.getName();

            String acquiredAt = null;
            if (item.getAcquiredAt() != null) {
                OffsetDateTime now = OffsetDateTime.now();
                acquiredAt = OffsetDateTimeUtils.formatRelativeTime(
                    now,
                    item.getAcquiredAt().getValue()
                );
            }

            StringBuilder tooltipBuilder = new StringBuilder();
            tooltipBuilder.append("<html>");
            tooltipBuilder.append(String.format("<b>%s</b><br>", name));
            if (acquiredBy != null) {
                tooltipBuilder.append(String.format(
                    "<p><font color='gray'>by </font>%s</p>",
                    acquiredBy
                ));
            }
            String droppedByNPCName = listItem.getDroppedByNPCName();
            if (droppedByNPCName != null) {
                tooltipBuilder.append(String.format(
                    "<p><font color='gray'>drop from </font>%s</p>",
                    droppedByNPCName
                ));
            }
            if (acquiredAt != null) {
                tooltipBuilder.append(String.format("<font color='gray'>%s</font>", acquiredAt));
            }
            tooltipBuilder.append("</html>");

            label.setToolTipText(tooltipBuilder.toString());

            return label;
        });

        scheduleRelativeTimeUpdate(list);

        Consumer<List<MainViewViewModel.ListItem>> setter = (newListItems) -> Bindings.invokeOnEDT(() -> {
            if (newListItems == null) {
                list.setListData(new MainViewViewModel.ListItem[0]);
                return;
            }
            list.setListData(newListItems.toArray(new MainViewViewModel.ListItem[0]));
        });

        PropertyChangeListener listItemsListener = (event) -> {
            @SuppressWarnings("unchecked")
            List<MainViewViewModel.ListItem> newListItems = (List<MainViewViewModel.ListItem>) event.getNewValue();
            setter.accept(newListItems);
        };
        viewModel.unlockedItemListItems.addListener(listItemsListener);
        setter.accept(viewModel.unlockedItemListItems.get());

        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        listBinding = () -> viewModel.unlockedItemListItems.removeListener(listItemsListener);

        return panel;
    }

    private void scheduleRelativeTimeUpdate(JList<?> list) {
        // cancel previous timer if active
        if (relativeTimeUpdateTimer != null && relativeTimeUpdateTimer.isRunning()) {
            relativeTimeUpdateTimer.stop();
        }

        // start a new 1-minute timer
        relativeTimeUpdateTimer = new Timer(
            60_000, e -> {
            list.repaint();               // update visible relative times
            scheduleRelativeTimeUpdate(list); // restart countdown
        }
        );
        relativeTimeUpdateTimer.setRepeats(false);
        relativeTimeUpdateTimer.start();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        MainView create(MainViewViewModel viewModel);
    }

    private static final class FactoryImpl implements Factory {

        @Inject
        private BUResourceService buResourceService;

        @Override
        public MainView create(MainViewViewModel viewModel) {
            return new MainView(viewModel, buResourceService);
        }
    }
}
