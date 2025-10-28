package com.elertan;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.data.MembersDataProvider;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.panel.BUPanel;
import com.elertan.panel2.BUPanel2;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

import javax.swing.*;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class BUPanelService implements BUPluginLifecycle {
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private BUResourceService buResourceService;
    @Inject
    private AccountConfigurationService accountConfigurationService;

    @Inject
    private Provider<BUPanel2> buPanelProvider;

    private BUPanel2 buPanel;
    private NavigationButton panelNavigationButton;

    private final Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;

    @Override
    public void startUp() {
//        buPanel = new BUPanel(buResourceService, httpClient, client, clientThread, gson, itemManager, buPanelService, accountConfigurationService, unlockedItemsDataProvider, gameRulesDataProvider, membersDataProvider);
        buPanel = buPanelProvider.get();
        panelNavigationButton = NavigationButton.builder()
                .tooltip("Bronzeman Unleashed")
                .icon(buResourceService.getIconBufferedImage())
                .priority(3)
                .panel(buPanel)
                .build();
        clientToolbar.addNavigation(panelNavigationButton);

        accountConfigurationService.addCurrentAccountConfigurationChangeListener(currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(currentAccountConfigurationChangeListener);

        clientToolbar.removeNavigation(panelNavigationButton);
        panelNavigationButton = null;
        buPanel.close();
        buPanel = null;
    }

    public void openPanel() {
        SwingUtilities.invokeLater(() -> clientToolbar.openPanel(panelNavigationButton));
    }

    public void closePanel() {
        SwingUtilities.invokeLater(() -> clientToolbar.openPanel(null));
    }

    private void currentAccountConfigurationChangeListener(AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            openPanel();
        }
    }
}
