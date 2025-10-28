package com.elertan.panel2.screens.main;

import com.elertan.panel2.screens.main.unlockedItems.LoadingScreen;
import com.elertan.panel2.screens.main.unlockedItems.ItemsScreen;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.swing.*;
import java.awt.*;

public class UnlockedItemsScreen extends JPanel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        UnlockedItemsScreen create(UnlockedItemsScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject
        private LoadingScreen.Factory loadingScreenFactory;
        @Inject
        private ItemsScreen.Factory itemsScreenFactory;

        @Override
        public UnlockedItemsScreen create(UnlockedItemsScreenViewModel viewModel) {
            return new UnlockedItemsScreen(viewModel, loadingScreenFactory, itemsScreenFactory);
        }
    }

    private final UnlockedItemsScreenViewModel viewModel;
    private final LoadingScreen.Factory loadingScreenFactory;
    private final ItemsScreen.Factory itemsScreenFactory;
    private final AutoCloseable cardLayoutBinding;

    private UnlockedItemsScreen(UnlockedItemsScreenViewModel viewModel, LoadingScreen.Factory loadingScreenFactory, ItemsScreen.Factory itemsScreenFactory) {
        this.viewModel = viewModel;
        this.loadingScreenFactory = loadingScreenFactory;
        this.itemsScreenFactory = itemsScreenFactory;

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);

        cardLayoutBinding = Bindings.bindCardLayout(
                this,
                cardLayout,
                viewModel.allUnlockedItems.derive(list -> list == null ? UnlockedItemsScreenViewModel.Screen.LOADING : UnlockedItemsScreenViewModel.Screen.ITEMS),
                this::buildScreen
        );
    }


    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
    }

    private JPanel buildScreen(UnlockedItemsScreenViewModel.Screen screen) {
        switch (screen) {
            case LOADING:
                return loadingScreenFactory.create();
            case ITEMS:
                return itemsScreenFactory.create(viewModel.allUnlockedItems, viewModel.searchText, viewModel.sortedBy, viewModel.unlockedByAccountHash);
        }

        throw new IllegalStateException("Unknown view state: " + screen);
    }

}
