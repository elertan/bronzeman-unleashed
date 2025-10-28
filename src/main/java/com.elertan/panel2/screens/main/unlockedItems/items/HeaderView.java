package com.elertan.panel2.screens.main.unlockedItems.items;

import com.elertan.BUResourceService;
import com.elertan.panel2.BUPanel2;
import com.elertan.panel2.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.common.collect.ImmutableMap;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HeaderView extends JPanel implements AutoCloseable {
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

    private final AutoCloseable sortedByComboBoxBinding;
    private final AutoCloseable unlockedByComboBoxBinding;

    private HeaderView(HeaderViewViewModel viewModel, BUResourceService buResourceService) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 3));

        IconTextField searchField = new IconTextField();
        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setPreferredSize(new Dimension(BUPanel2.PANEL_WIDTH - 20, 30));
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.setMinimumSize(new Dimension(0, 30));

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

        JPanel searchbarHeader = new JPanel(new BorderLayout(5, 0));
        searchbarHeader.add(searchField, BorderLayout.CENTER);
        searchbarHeader.add(configButton, BorderLayout.EAST);

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
                .put(UnlockedItemsScreenViewModel.SortedBy.UNLOCKED_AT_ASC, "Unlocked at (asc)")
                .put(UnlockedItemsScreenViewModel.SortedBy.ALPHABETICAL_ASC, "Alphabetical (asc)")
                .put(UnlockedItemsScreenViewModel.SortedBy.PLAYER_ASC, "Player (asc)")
                .put(UnlockedItemsScreenViewModel.SortedBy.UNLOCKED_AT_DESC, "Unlocked at (desc)")
                .put(UnlockedItemsScreenViewModel.SortedBy.ALPHABETICAL_DESC, "Alphabetical (desc)")
                .put(UnlockedItemsScreenViewModel.SortedBy.PLAYER_DESC, "Player (desc)")
                .build();
        Property<List<UnlockedItemsScreenViewModel.SortedBy>> sortedByOptions = new Property<>(
                new ArrayList<>(sortedByEnumToStringMap.keySet())
        );
        sortedByComboBoxBinding = Bindings.bindComboBox(sortedByComboBox, sortedByOptions, viewModel.sortedBy, new Property<>(sortedByEnumToStringMap));

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

        unlockedByRow.add(unlockedByLabel, BorderLayout.WEST);
        unlockedByRow.add(unlockedByComboBox, BorderLayout.CENTER);

        filterAndSortPanel.add(unlockedByRow);

        add(filterAndSortPanel);
    }

    @Override
    public void close() throws Exception {
        unlockedByComboBoxBinding.close();
        sortedByComboBoxBinding.close();
    }

}
