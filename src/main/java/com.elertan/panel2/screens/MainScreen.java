package com.elertan.panel2.screens;

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

        @Override
        public MainScreen create(MainScreenViewModel viewModel) {
            UnlockedItemsScreenViewModel unlockedItemsScreenViewModel = unlockedItemsScreenViewModelFactory.create();

            return new MainScreen(viewModel, unlockedItemsScreenViewModel, unlockedItemsScreenFactory);
        }
    }

    private final UnlockedItemsScreenViewModel unlockedItemsScreenViewModel;
    private final UnlockedItemsScreen.Factory unlockedItemsScreenFactory;
    private final AutoCloseable cardLayoutBinding;

    private MainScreen(MainScreenViewModel viewModel, UnlockedItemsScreenViewModel unlockedItemsScreenViewModel, UnlockedItemsScreen.Factory unlockedItemsScreenFactory) {
        this.unlockedItemsScreenViewModel = unlockedItemsScreenViewModel;
        this.unlockedItemsScreenFactory = unlockedItemsScreenFactory;

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
                return new JPanel();
        }

        throw new IllegalStateException("Unknown main screen: " + screen);
    }
}
