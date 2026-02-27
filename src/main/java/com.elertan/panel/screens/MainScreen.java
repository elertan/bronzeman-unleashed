package com.elertan.panel.screens;

import com.elertan.panel.screens.main.ConfigScreen;
import com.elertan.panel.screens.main.ConfigScreenViewModel;
import com.elertan.panel.screens.main.UnlockedItemsScreen;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.CardLayout;
import javax.swing.JPanel;

public class MainScreen extends JPanel implements AutoCloseable {

    public final Property<Screen> screen = new Property<>(Screen.UNLOCKED_ITEMS);
    private final UnlockedItemsScreenViewModel unlockedItemsScreenViewModel;
    private final UnlockedItemsScreen.Factory unlockedItemsScreenFactory;
    private final ConfigScreenViewModel.Factory configScreenViewModelFactory;
    private final ConfigScreen.Factory configScreenFactory;
    private final AutoCloseable cardLayoutBinding;

    private MainScreen(
        UnlockedItemsScreenViewModel unlockedItemsScreenViewModel,
        UnlockedItemsScreen.Factory unlockedItemsScreenFactory,
        ConfigScreenViewModel.Factory configScreenViewModelFactory,
        ConfigScreen.Factory configScreenFactory
    ) {
        this.unlockedItemsScreenViewModel = unlockedItemsScreenViewModel;
        this.unlockedItemsScreenFactory = unlockedItemsScreenFactory;
        this.configScreenViewModelFactory = configScreenViewModelFactory;
        this.configScreenFactory = configScreenFactory;

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);
        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, screen, this::buildScreen);
    }

    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
    }

    public void navigateToConfig() {
        screen.set(Screen.CONFIG);
    }

    public void navigateToUnlockedItems() {
        screen.set(Screen.UNLOCKED_ITEMS);
    }

    private JPanel buildScreen(Screen s) {
        switch (s) {
            case UNLOCKED_ITEMS:
                return unlockedItemsScreenFactory.create(
                    unlockedItemsScreenViewModel, this::navigateToConfig);
            case CONFIG:
                return configScreenFactory.create(
                    configScreenViewModelFactory.create(this::navigateToUnlockedItems));
        }
        throw new IllegalStateException("Unknown main screen: " + s);
    }

    public enum Screen {
        UNLOCKED_ITEMS,
        CONFIG
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainScreen create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private UnlockedItemsScreenViewModel.Factory unlockedItemsScreenViewModelFactory;
        @Inject private UnlockedItemsScreen.Factory unlockedItemsScreenFactory;
        @Inject private ConfigScreenViewModel.Factory configScreenViewModelFactory;
        @Inject private ConfigScreen.Factory configScreenFactory;

        @Override
        public MainScreen create() {
            return new MainScreen(
                unlockedItemsScreenViewModelFactory.create(),
                unlockedItemsScreenFactory,
                configScreenViewModelFactory,
                configScreenFactory
            );
        }
    }
}
