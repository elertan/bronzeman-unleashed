package com.elertan.panel;

import com.elertan.panel.screens.*;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

public class BUPanel extends PluginPanel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        BUPanel create(BUPanelViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject
        private WaitForLoginScreen.Factory waitForLoginScreenFactory;
        @Inject
        private SetupScreenViewModel.Factory setupScreenViewModelFactory;
        @Inject
        private SetupScreen.Factory setupScreenFactory;
        @Inject
        private MainScreenViewModel.Factory mainScreenViewModelFactory;
        @Inject
        private MainScreen.Factory mainScreenFactory;

        @Override
        public BUPanel create(BUPanelViewModel viewModel) {
            SetupScreenViewModel setupScreenViewModel = setupScreenViewModelFactory.create();
            MainScreenViewModel mainScreenViewModel = mainScreenViewModelFactory.create();
            return new BUPanel(viewModel, waitForLoginScreenFactory, setupScreenViewModel, setupScreenFactory, mainScreenViewModel, mainScreenFactory);
        }
    }

    private final BUPanelViewModel viewModel;
    private final WaitForLoginScreen.Factory waitForLoginScreenFactory;
    private final SetupScreenViewModel setupScreenViewModel;
    private final SetupScreen.Factory setupScreenFactory;
    private final MainScreenViewModel mainScreenViewModel;
    private final MainScreen.Factory mainScreenFactory;

    private final AutoCloseable cardLayoutBinding;

    private BUPanel(
            BUPanelViewModel viewModel,
            WaitForLoginScreen.Factory waitForLoginScreenFactory,
            SetupScreenViewModel setupScreenViewModel,
            SetupScreen.Factory setupScreenFactory,
            MainScreenViewModel mainScreenViewModel,
            MainScreen.Factory mainScreenFactory
    ) {
        super(false);

        this.viewModel = viewModel;
        this.waitForLoginScreenFactory = waitForLoginScreenFactory;
        this.setupScreenViewModel = setupScreenViewModel;
        this.setupScreenFactory = setupScreenFactory;
        this.mainScreenViewModel = mainScreenViewModel;
        this.mainScreenFactory = mainScreenFactory;

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);

        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.screen, this::buildScreen);
    }

    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
        viewModel.close();
    }

    private JPanel buildScreen(BUPanelViewModel.Screen screen) {
        switch (screen) {
            case WAIT_FOR_LOGIN:
                return waitForLoginScreenFactory.create();
            case SETUP:
                return setupScreenFactory.create(setupScreenViewModel);
            case MAIN:
                return mainScreenFactory.create(mainScreenViewModel);
        }

        throw new IllegalStateException("Unknown screen: " + screen);
    }
}
