package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.BUResourceService;
import com.elertan.panel.BUPanel;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.common.collect.ImmutableMap;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

@Slf4j
public class HeaderView extends JPanel implements AutoCloseable {

    private final AutoCloseable searchFieldBinding;
    private final AutoCloseable sortedByComboBoxBinding;
    private final AutoCloseable unlockedByComboBoxBinding;
    private final AutoCloseable unlockedByRowVisibleBinding;
    private final AutoCloseable manualUnlockButtonVisibleBinding;

    private HeaderView(HeaderViewViewModel viewModel, BUResourceService buResourceService) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 3));

        IconTextField searchField = new IconTextField();
        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setPreferredSize(new Dimension(BUPanel.PANEL_WIDTH - 20, 30));
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.setMinimumSize(new Dimension(0, 30));
        searchFieldBinding = Bindings.bindIconTextFieldText(searchField, viewModel.searchText);

        JButton configButton = createHeaderButton();
        configButton.setIcon(new ImageIcon(buResourceService.getConfigureIconBufferedImage()));
        configButton.setToolTipText("Open configuration");
        configButton.addActionListener(e -> viewModel.onOpenConfigurationClick());

        JButton manualUnlockButton = createHeaderButton();
        manualUnlockButton.setText("+");
        manualUnlockButton.setFont(FontManager.getRunescapeBoldFont());
        manualUnlockButton.setToolTipText("Unlock an item by hand");
        manualUnlockButton.addActionListener(e ->
            viewModel.onManualUnlockClick(SwingUtilities.getWindowAncestor(this)));
        manualUnlockButtonVisibleBinding = Bindings.bindVisible(
            manualUnlockButton,
            viewModel.showManualUnlockButton
        );

        JPanel headerButtons = new JPanel(new BorderLayout(5, 0));
        headerButtons.add(manualUnlockButton, BorderLayout.WEST);
        headerButtons.add(configButton, BorderLayout.EAST);

        JPanel searchbarHeader = new JPanel(new BorderLayout(5, 0));
        searchbarHeader.add(searchField, BorderLayout.CENTER);
        searchbarHeader.add(headerButtons, BorderLayout.EAST);

        add(searchbarHeader);

        add(Box.createVerticalStrut(8));

        JPanel filterAndSortPanel = new JPanel();
        filterAndSortPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));
        filterAndSortPanel.setLayout(new BoxLayout(filterAndSortPanel, BoxLayout.Y_AXIS));

        JPanel sortedByRow = new JPanel(new BorderLayout(5, 0));
        JLabel sortedByLabel = new JLabel("Sorted by:");
        sortedByLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        JComboBox<UnlockedItemsScreenViewModel.SortedBy> sortedByComboBox = new JComboBox<>();

        Map<UnlockedItemsScreenViewModel.SortedBy, String> sortedByEnumToStringMap = ImmutableMap.<UnlockedItemsScreenViewModel.SortedBy, String>builder()
            .put(
                UnlockedItemsScreenViewModel.SortedBy.UNLOCKED_AT_ASC,
                "Unlocked at (asc)"
            )
            .put(
                UnlockedItemsScreenViewModel.SortedBy.ALPHABETICAL_ASC,
                "Alphabetical (asc)"
            )
            .put(
                UnlockedItemsScreenViewModel.SortedBy.PLAYER_ASC,
                "Player (asc)"
            )
            .put(
                UnlockedItemsScreenViewModel.SortedBy.UNLOCKED_AT_DESC,
                "Unlocked at (desc)"
            )
            .put(
                UnlockedItemsScreenViewModel.SortedBy.ALPHABETICAL_DESC,
                "Alphabetical (desc)"
            )
            .put(
                UnlockedItemsScreenViewModel.SortedBy.PLAYER_DESC,
                "Player (desc)"
            )
            .build();
        sortedByComboBoxBinding = Bindings.bindComboBox(
            sortedByComboBox,
            viewModel.sortedByOptions,
            viewModel.sortedBy,
            new Property<>(sortedByEnumToStringMap)
        );

        sortedByRow.add(sortedByLabel, BorderLayout.WEST);
        sortedByRow.add(sortedByComboBox, BorderLayout.CENTER);

        filterAndSortPanel.add(sortedByRow);

        filterAndSortPanel.add(Box.createVerticalStrut(5));

        JPanel unlockedByRow = new JPanel(new BorderLayout(5, 0));
        JLabel unlockedByLabel = new JLabel("Unlocked by:");
        unlockedByLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        JComboBox<Long> unlockedByComboBox = new JComboBox<>();

        Property<List<Long>> unlockedByOptions = viewModel
            .accountHashesFromAllUnlockedItems
            .derive((list) -> {
                List<Long> result = new ArrayList<>(list.size() + 1);
                // The "Everyone" option
                result.add(null);
                result.addAll(list);
                return result;
            });

        Property<Map<Long, String>> unlockedByValueToStringMapProperty = viewModel
            .accountHashToMemberNameMap
            .derive((map) -> {
                Map<Long, String> result = new HashMap<>(map);
                result.put(null, "Everyone");
                return result;
            });

        unlockedByComboBoxBinding = Bindings.bindComboBox(
            unlockedByComboBox,
            unlockedByOptions,
            viewModel.unlockedByAccountHash,
            unlockedByValueToStringMapProperty
        );
        unlockedByRowVisibleBinding = Bindings.bindVisible(
            unlockedByRow,
            viewModel.showUnlockedByFilter
        );

        unlockedByRow.add(unlockedByLabel, BorderLayout.WEST);
        unlockedByRow.add(unlockedByComboBox, BorderLayout.CENTER);

        filterAndSortPanel.add(unlockedByRow);

        add(filterAndSortPanel);
    }

    private static JButton createHeaderButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(30, 30));
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });
        return button;
    }

    @Override
    public void close() throws Exception {
        manualUnlockButtonVisibleBinding.close();
        unlockedByRowVisibleBinding.close();
        unlockedByComboBoxBinding.close();
        sortedByComboBoxBinding.close();
        searchFieldBinding.close();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        HeaderView create(HeaderViewViewModel viewModel);
    }

    private static final class FactoryImpl implements Factory {

        @Inject
        private BUResourceService buResourceService;

        @Override
        public HeaderView create(HeaderViewViewModel viewModel) {
            return new HeaderView(viewModel, buResourceService);
        }
    }

}
