package com.elertan.panel2.screens.main;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

public class UnlockedItemsScreenViewModel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        UnlockedItemsScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public UnlockedItemsScreenViewModel create() {
            return new UnlockedItemsScreenViewModel();
        }
    }

    private UnlockedItemsScreenViewModel() {
    }
}
