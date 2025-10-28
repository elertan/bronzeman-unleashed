package com.elertan.panel2.screens;

import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

public class MainScreenViewModel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public MainScreenViewModel create() {
            return new MainScreenViewModel();
        }
    }

    public enum MainScreen {
        UNLOCKED_ITEMS,
        CONFIG
    }

    public final Property<MainScreen> mainScreen = new Property<>(MainScreen.UNLOCKED_ITEMS);

    private MainScreenViewModel() {
    }

    public void onUnlockedItemsNavigateToConfig() {
        mainScreen.set(MainScreen.CONFIG);
    }

    public void onConfigNavigateToUnlockedItems() {
        mainScreen.set(MainScreen.UNLOCKED_ITEMS);
    }
}
