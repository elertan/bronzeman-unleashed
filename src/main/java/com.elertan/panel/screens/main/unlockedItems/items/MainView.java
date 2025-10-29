package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.BUResourceService;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.ui.Bindings;
import com.elertan.utils.OffsetDateTimeUtils;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

public class MainView extends JPanel implements AutoCloseable {
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
        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.viewState, this::buildViewState);
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

        JList<MainViewViewModel.ListItem> list = new JList<>();
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
            String acquiredBy = acquiredByMember == null ? null : acquiredByMember.getName();

            String acquiredAt = null;
            if (item.getAcquiredAt() != null) {
                OffsetDateTime now = OffsetDateTime.now();
                acquiredAt = OffsetDateTimeUtils.formatRelativeTime(now, item.getAcquiredAt().getValue());
            }

            StringBuilder tooltipBuilder = new StringBuilder();
            tooltipBuilder.append("<html>");
            tooltipBuilder.append(String.format("<b>%s</b><br>", name));
            if (acquiredBy != null) {
                tooltipBuilder.append(String.format("<p><font color='gray'>by </font>%s</p>", acquiredBy));
            }
            String droppedByNPCName = listItem.getDroppedByNPCName();
            if (droppedByNPCName != null) {
                tooltipBuilder.append(String.format("<p><font color='gray'>drop from </font>%s</p>", droppedByNPCName));
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
        relativeTimeUpdateTimer = new Timer(60_000, e -> {
            list.repaint();               // update visible relative times
            scheduleRelativeTimeUpdate(list); // restart countdown
        });
        relativeTimeUpdateTimer.setRepeats(false);
        relativeTimeUpdateTimer.start();
    }
}
