package com.elertan.panel.screens;

import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

public class MainScreenViewModel {

    public final Property<MainScreen> mainScreen = new Property<>(MainScreen.UNLOCKED_ITEMS);
    private volatile boolean isNotCounted;

    private MainScreenViewModel() {
    }

    public void navigateToConfig() {
        mainScreen.set(MainScreen.CONFIG);
    }

    public void navigateToUnlockedItems() {
        mainScreen.set(isNotCounted ? MainScreen.COUNT_ITEMS : MainScreen.UNLOCKED_ITEMS);
    }

    /** An existing account that is not counted sees the count card instead of the unlocks. */
    public void setNotCounted(boolean notCounted) {
        isNotCounted = notCounted;
        MainScreen current = mainScreen.get();
        if (notCounted && current == MainScreen.UNLOCKED_ITEMS) {
            mainScreen.set(MainScreen.COUNT_ITEMS);
        } else if (!notCounted && current == MainScreen.COUNT_ITEMS) {
            mainScreen.set(MainScreen.UNLOCKED_ITEMS);
        }
    }

    public enum MainScreen {
        UNLOCKED_ITEMS,
        CONFIG,
        COUNT_ITEMS
    }

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
}
