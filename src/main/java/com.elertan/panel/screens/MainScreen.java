package com.elertan.panel.screens;

import com.elertan.ItemLockService;
import com.elertan.panel.screens.main.CountItemsView;
import com.elertan.panel.screens.main.CountItemsViewModel;
import com.elertan.utils.Subscription;
import com.elertan.panel.screens.main.ConfigScreen;
import com.elertan.panel.screens.main.ConfigScreenViewModel;
import com.elertan.panel.screens.main.UnlockedItemsScreen;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.CardLayout;
import javax.swing.JPanel;

public class MainScreen extends JPanel implements AutoCloseable {

    private final MainScreenViewModel viewModel;
    private final UnlockedItemsScreenViewModel unlockedItemsScreenViewModel;
    private final UnlockedItemsScreen.Factory unlockedItemsScreenFactory;
    private final ConfigScreenViewModel configScreenViewModel;
    private final ConfigScreen.Factory configScreenFactory;
    private final CountItemsViewModel countItemsViewModel;
    private final Subscription itemLockStatusSubscription;
    private final AutoCloseable cardLayoutBinding;

    private MainScreen(MainScreenViewModel viewModel,
        UnlockedItemsScreenViewModel unlockedItemsScreenViewModel,
        UnlockedItemsScreen.Factory unlockedItemsScreenFactory,
        ConfigScreenViewModel configScreenViewModel,
        ConfigScreen.Factory configScreenFactory,
        CountItemsViewModel countItemsViewModel,
        ItemLockService itemLockService) {
        this.viewModel = viewModel;
        this.unlockedItemsScreenViewModel = unlockedItemsScreenViewModel;
        this.unlockedItemsScreenFactory = unlockedItemsScreenFactory;
        this.configScreenViewModel = configScreenViewModel;
        this.configScreenFactory = configScreenFactory;
        this.countItemsViewModel = countItemsViewModel;

        itemLockStatusSubscription = itemLockService.getStatus().subscribeImmediate((status, old) ->
            viewModel.setNotCounted(status == ItemLockService.Status.NOT_COUNTED));

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);

        cardLayoutBinding = Bindings.bindCardLayout(
            this,
            cardLayout,
            viewModel.mainScreen,
            this::buildScreen
        );
    }

    @Override
    public void close() throws Exception {
        itemLockStatusSubscription.dispose();
        cardLayoutBinding.close();
        countItemsViewModel.close();
        configScreenViewModel.close();
        unlockedItemsScreenViewModel.close();
    }

    private JPanel buildScreen(MainScreenViewModel.MainScreen screen) {
        switch (screen) {
            case UNLOCKED_ITEMS:
                return unlockedItemsScreenFactory.create(
                    unlockedItemsScreenViewModel,
                    viewModel::navigateToConfig
                );
            case CONFIG:
                return configScreenFactory.create(configScreenViewModel);
            case COUNT_ITEMS:
                return new CountItemsView(countItemsViewModel);
        }

        throw new IllegalStateException("Unknown main screen: " + screen);
    }

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
        @Inject
        private CountItemsViewModel.Factory countItemsViewModelFactory;
        @Inject
        private ItemLockService itemLockService;

        @Override
        public MainScreen create(MainScreenViewModel viewModel) {
            UnlockedItemsScreenViewModel unlockedItemsScreenViewModel = unlockedItemsScreenViewModelFactory.create();
            ConfigScreenViewModel configScreenViewModel = configScreenViewModelFactory.create(
                viewModel::navigateToUnlockedItems);

            CountItemsViewModel countItemsViewModel = countItemsViewModelFactory.create(viewModel::navigateToConfig);

            return new MainScreen(
                viewModel,
                unlockedItemsScreenViewModel,
                unlockedItemsScreenFactory,
                configScreenViewModel,
                configScreenFactory,
                countItemsViewModel,
                itemLockService
            );
        }
    }
}
