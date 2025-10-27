package com.elertan.panel2;

import com.elertan.panel2.screens.SetupScreen;
import com.elertan.panel2.screens.WaitForLoginScreen;
import com.elertan.ui.Bindings;
import com.google.inject.Inject;
import com.google.inject.Provider;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

public class BUPanel2 extends PluginPanel implements AutoCloseable {
    private final BUPanelViewModel viewModel;
    private final Provider<WaitForLoginScreen> waitForLoginScreenProvider;
    private final Provider<SetupScreen> setupScreenProvider;

    private final AutoCloseable cardLayoutBinding;

    @Inject
    public BUPanel2(Provider<BUPanelViewModel> viewModelProvider, Provider<WaitForLoginScreen> waitForLoginScreenProvider, Provider<SetupScreen> setupScreenProvider) {
        super(false);

        viewModel = viewModelProvider.get();
        this.waitForLoginScreenProvider = waitForLoginScreenProvider;
        this.setupScreenProvider = setupScreenProvider;

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
                return waitForLoginScreenProvider.get();
            case SETUP:
                return setupScreenProvider.get();
            case MAIN:
                return new JPanel();
        }

        throw new IllegalStateException("Unknown screen: " + screen);
    }
}
