package com.elertan.panel2.screens.main.unlockedItems.items;

import com.elertan.models.UnlockedItem;
import com.elertan.panel2.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainViewViewModel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainViewViewModel create(Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText, Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public MainViewViewModel create(Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText, Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash) {
            return new MainViewViewModel(allUnlockedItems, searchText, sortedBy, unlockedByAccountHash);
        }
    }

    public final Property<List<UnlockedItem>> unlockedItems;

    private MainViewViewModel(Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText, Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash) {
        unlockedItems = Property.deriveManyAsync(
                Arrays.asList(allUnlockedItems, searchText, sortedBy, unlockedByAccountHash),
                (values) -> {
                    @SuppressWarnings("unchecked")
                    List<UnlockedItem> allUnlockedItemsValue = (List<UnlockedItem>) values.get(0);
                    String searchTextValue = (String) values.get(1);
                    UnlockedItemsScreenViewModel.SortedBy sortedByValue = (UnlockedItemsScreenViewModel.SortedBy) values.get(2);
                    Long unlockedByAccountHashValue = (Long) values.get(3);

                    if (allUnlockedItemsValue == null) {
                        return null;
                    }

                    String searchTextLowerCase = searchTextValue == null ? null : searchTextValue.toLowerCase().trim();
                    if (searchTextLowerCase != null && searchTextLowerCase.isEmpty()) {
                        searchTextLowerCase = null;
                    }

                    final String searchTerm = searchTextLowerCase;
                    return allUnlockedItemsValue.stream()
                            .filter(item -> {
                                if (unlockedByAccountHashValue != null && item.getAcquiredByAccountHash() != unlockedByAccountHashValue) {
                                    return false;
                                }
                                return searchTerm == null || item.getName().toLowerCase().contains(searchTerm);
                            })
                            .collect(Collectors.toList());
                }
        );
    }

    @Override
    public void close() throws Exception {

    }
}
