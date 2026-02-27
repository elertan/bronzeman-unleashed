package com.elertan.panel;

import com.elertan.AccountConfigurationService;
import com.elertan.models.AccountConfiguration;
import com.elertan.panel.screens.MainScreen;
import com.elertan.panel.screens.SetupScreen;
import com.elertan.panel.screens.SetupScreenViewModel;
import com.elertan.panel.screens.WaitForLoginScreen;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.elertan.utils.Subscription;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.CardLayout;
import javax.swing.JPanel;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.PluginPanel;

public class BUPanel extends PluginPanel implements AutoCloseable {

    public final Property<Screen> screen = new Property<>(Screen.WAIT_FOR_LOGIN);
    private final AutoCloseable cardLayoutBinding;
    private Subscription accountConfigSubscription;

    private BUPanel(
        AccountConfigurationService accountConfigurationService,
        Client client,
        WaitForLoginScreen.Factory waitForLoginScreenFactory,
        SetupScreenViewModel.Factory setupScreenViewModelFactory,
        SetupScreen.Factory setupScreenFactory,
        MainScreen.Factory mainScreenFactory
    ) {
        super(false);

        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::setScreenForAccountConfiguration);
        if (accountConfigurationService.isReady() && client.getGameState() == GameState.LOGGED_IN) {
            setScreenForAccountConfiguration(accountConfigurationService.getCurrentAccountConfiguration());
        }

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);
        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, screen, s -> {
            switch (s) {
                case WAIT_FOR_LOGIN: return waitForLoginScreenFactory.create();
                case SETUP: return setupScreenFactory.create(setupScreenViewModelFactory.create());
                case MAIN: return mainScreenFactory.create();
            }
            throw new IllegalStateException("Unknown screen: " + s);
        });
    }

    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
    }

    private void setScreenForAccountConfiguration(AccountConfiguration config) {
        screen.set(config == null ? Screen.SETUP : Screen.MAIN);
    }

    public enum Screen {
        WAIT_FOR_LOGIN, SETUP, MAIN
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        BUPanel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private AccountConfigurationService accountConfigurationService;
        @Inject private Client client;
        @Inject private WaitForLoginScreen.Factory waitForLoginScreenFactory;
        @Inject private SetupScreenViewModel.Factory setupScreenViewModelFactory;
        @Inject private SetupScreen.Factory setupScreenFactory;
        @Inject private MainScreen.Factory mainScreenFactory;

        @Override
        public BUPanel create() {
            return new BUPanel(
                accountConfigurationService, client,
                waitForLoginScreenFactory, setupScreenViewModelFactory,
                setupScreenFactory, mainScreenFactory
            );
        }
    }
}
