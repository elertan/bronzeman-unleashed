package com.elertan.panel.screens.main.unlockedItems;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

public class ItemsScreenViewModel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        ItemsScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public ItemsScreenViewModel create() {
            return new ItemsScreenViewModel();
        }
    }
}
