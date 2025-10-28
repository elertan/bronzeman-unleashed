package com.elertan.panel2.screens;

import com.elertan.panel2.screens.main.ConfigScreen;
import com.elertan.panel2.screens.main.ConfigScreenViewModel;
import com.elertan.panel2.screens.main.UnlockedItemsScreen;
import com.elertan.panel2.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.swing.*;
import java.awt.*;

public class MainScreen extends JPanel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainScreen create(MainScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject
        private UnlockedItemsScreenViewModel.Factory unlockedItemsScreenViewModelFactory;
        @Inject
        private UnlockedItemsScreen.Factory unlockedItemsScreenFactory;
        @Inject
        private ConfigScreenViewModel.Factory configScreenViewModelFactory;
        @Inject
        private ConfigScreen.Factory configScreenFactory;

        @Override
        public MainScreen create(MainScreenViewModel viewModel) {
            UnlockedItemsScreenViewModel unlockedItemsScreenViewModel = unlockedItemsScreenViewModelFactory.create();
            ConfigScreenViewModel configScreenViewModel = configScreenViewModelFactory.create();

            return new MainScreen(viewModel, unlockedItemsScreenViewModel, unlockedItemsScreenFactory, configScreenViewModel, configScreenFactory);
        }
    }

    private final UnlockedItemsScreenViewModel unlockedItemsScreenViewModel;
    private final UnlockedItemsScreen.Factory unlockedItemsScreenFactory;
    private final ConfigScreenViewModel configScreenViewModel;
    private final ConfigScreen.Factory configScreenFactory;
    private final AutoCloseable cardLayoutBinding;

    private MainScreen(
            MainScreenViewModel viewModel,
            UnlockedItemsScreenViewModel unlockedItemsScreenViewModel,
            UnlockedItemsScreen.Factory unlockedItemsScreenFactory,
            ConfigScreenViewModel configScreenViewModel,
            ConfigScreen.Factory configScreenFactory
    ) {
        this.unlockedItemsScreenViewModel = unlockedItemsScreenViewModel;
        this.unlockedItemsScreenFactory = unlockedItemsScreenFactory;
        this.configScreenViewModel = configScreenViewModel;
        this.configScreenFactory = configScreenFactory;

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);

        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.mainScreen, this::buildScreen);
    }

    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
    }


    private JPanel buildScreen(MainScreenViewModel.MainScreen screen) {
        switch (screen) {
            case UNLOCKED_ITEMS:
                return unlockedItemsScreenFactory.create(unlockedItemsScreenViewModel);
            case CONFIG:
                return configScreenFactory.create(configScreenViewModel);
        }

        throw new IllegalStateException("Unknown main screen: " + screen);
    }
}
