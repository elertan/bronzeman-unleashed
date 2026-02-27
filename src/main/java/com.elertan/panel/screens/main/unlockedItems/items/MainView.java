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
import java.util.List;
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
        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.viewState, this::buildViewState);
    }

    @Override
    public void close() throws Exception {
        if (relativeTimeUpdateTimer != null && relativeTimeUpdateTimer.isRunning()) relativeTimeUpdateTimer.stop();
        if (listBinding != null) listBinding.close();
        cardLayoutBinding.close();
    }

    private JPanel buildViewState(MainViewViewModel.ViewState viewState) {
        switch (viewState) {
            case LOADING:
                JPanel loadingPanel = new JPanel(new GridBagLayout());
                JLabel spinner = new JLabel();
                spinner.setIcon(buResourceService.getLoadingSpinnerImageIcon());
                spinner.setHorizontalAlignment(SwingConstants.CENTER);
                loadingPanel.add(spinner);
                return loadingPanel;
            case EMPTY:
                JPanel emptyPanel = new JPanel(new GridBagLayout());
                JLabel emptyLabel = new JLabel("Nothing unlocked yet");
                emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
                emptyPanel.add(emptyLabel);
                return emptyPanel;
            case READY:
                return buildReadyViewState();
        }
        throw new IllegalStateException("Unknown view state: " + viewState);
    }

    private JPanel buildReadyViewState() {
        JPanel panel = new JPanel(new BorderLayout());
        JList<MainViewViewModel.ListItem> list = new JList<>();
        list.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        list.setBackground(ColorScheme.DARK_GRAY_COLOR);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);

        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("Remove from unlocked items");
        contextMenu.add(removeItem);
        removeItem.addActionListener(e -> {
            MainViewViewModel.ListItem selected = list.getSelectedValue();
            if (selected != null) viewModel.removeFromUnlockedItems(selected);
        });

        list.addMouseListener(new MouseAdapter() {
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index != -1) { list.setSelectedIndex(index); contextMenu.show(list, e.getX(), e.getY()); }
                }
            }
            @Override public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
        });

        list.setCellRenderer((jl, listItem, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            AsyncBufferedImage icon = listItem.getIcon();
            icon.addTo(label);
            label.setToolTipText(buildTooltip(listItem));
            return label;
        });

        scheduleRelativeTimeUpdate(list);

        Consumer<List<MainViewViewModel.ListItem>> setter = newItems -> Bindings.invokeOnEDT(() -> {
            list.setListData(newItems == null
                ? new MainViewViewModel.ListItem[0]
                : newItems.toArray(new MainViewViewModel.ListItem[0]));
        });

        PropertyChangeListener listItemsListener = event -> {
            @SuppressWarnings("unchecked")
            List<MainViewViewModel.ListItem> newItems = (List<MainViewViewModel.ListItem>) event.getNewValue();
            setter.accept(newItems);
        };
        viewModel.unlockedItemListItems.addListener(listItemsListener);
        setter.accept(viewModel.unlockedItemListItems.get());

        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        listBinding = () -> viewModel.unlockedItemListItems.removeListener(listItemsListener);
        return panel;
    }

    private String buildTooltip(MainViewViewModel.ListItem listItem) {
        UnlockedItem item = listItem.getItem();
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("<b>%s</b><br>", item.getName()));
        Member acquiredBy = listItem.getAcquiredByMember();
        if (acquiredBy != null)
            sb.append(String.format("<p><font color='gray'>by </font>%s</p>", acquiredBy.getName()));
        String npcName = listItem.getDroppedByNPCName();
        if (npcName != null)
            sb.append(String.format("<p><font color='gray'>drop from </font>%s</p>", npcName));
        if (item.getAcquiredAt() != null) {
            String relTime = OffsetDateTimeUtils.formatRelativeTime(OffsetDateTime.now(), item.getAcquiredAt().getValue());
            sb.append(String.format("<font color='gray'>%s</font>", relTime));
        }
        sb.append("</html>");
        return sb.toString();
    }

    private void scheduleRelativeTimeUpdate(JList<?> list) {
        if (relativeTimeUpdateTimer != null && relativeTimeUpdateTimer.isRunning()) relativeTimeUpdateTimer.stop();
        relativeTimeUpdateTimer = new Timer(60_000, e -> {
            list.repaint();
            scheduleRelativeTimeUpdate(list);
        });
        relativeTimeUpdateTimer.setRepeats(false);
        relativeTimeUpdateTimer.start();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainView create(MainViewViewModel viewModel);
    }

    private static final class FactoryImpl implements Factory {
        @Inject private BUResourceService buResourceService;

        @Override
        public MainView create(MainViewViewModel viewModel) {
            return new MainView(viewModel, buResourceService);
        }
    }
}
