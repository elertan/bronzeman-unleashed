package com.elertan.panel.screens.main;

import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.UnlockedItem;
import com.elertan.panel.BaseViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnlockedItemsScreenViewModel extends BaseViewModel {

    public final Property<List<UnlockedItem>> allUnlockedItems;
    public final Property<String> searchText = new Property<>("");
    public final Property<SortedBy> sortedBy = new Property<>(SortedBy.UNLOCKED_AT_DESC);
    public final Property<Long> unlockedByAccountHash = new Property<>(null);
    private final UnlockedItemsDataProvider unlockedItemsDataProvider;
    private final UnlockedItemsDataProvider.UnlockedItemsMapListener unlockedItemsMapListener;
    private final PropertyChangeListener sortedByListener = this::sortedByListener;

    private UnlockedItemsScreenViewModel(UnlockedItemsDataProvider unlockedItemsDataProvider) {
        this.unlockedItemsDataProvider = unlockedItemsDataProvider;

        Supplier<List<UnlockedItem>> supplier = () -> {
            Map<Integer, UnlockedItem> map = unlockedItemsDataProvider.getUnlockedItemsMap();
            return map == null ? null : new ArrayList<>(map.values());
        };
        allUnlockedItems = new Property<>(supplier.get());
        unlockedItemsMapListener = new UnlockedItemsDataProvider.UnlockedItemsMapListener() {
            @Override public void onUpdate(UnlockedItem item) { allUnlockedItems.set(supplier.get()); }
            @Override public void onDelete(UnlockedItem item) { allUnlockedItems.set(supplier.get()); }
        };
        unlockedItemsDataProvider.addUnlockedItemsMapListener(unlockedItemsMapListener);
        unlockedItemsDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) return;
            allUnlockedItems.set(supplier.get());
        });
        addListener(sortedBy, sortedByListener);
    }

    @Override
    public void close() throws Exception {
        super.close();
        unlockedItemsDataProvider.removeUnlockedItemsMapListener(unlockedItemsMapListener);
    }

    private void sortedByListener(PropertyChangeEvent event) {
        log.info("sorted by changed to: {}", event.getNewValue());
    }

    public enum Screen { LOADING, ITEMS }
    public enum SortedBy { UNLOCKED_AT_ASC, ALPHABETICAL_ASC, PLAYER_ASC, UNLOCKED_AT_DESC, ALPHABETICAL_DESC, PLAYER_DESC }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        UnlockedItemsScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private UnlockedItemsDataProvider unlockedItemsDataProvider;

        @Override
        public UnlockedItemsScreenViewModel create() {
            return new UnlockedItemsScreenViewModel(unlockedItemsDataProvider);
        }
    }
}
