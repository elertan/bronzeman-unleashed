package com.elertan;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.data.MembersDataProvider;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.panel.BUPanel;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

import javax.swing.*;

@Slf4j
@Singleton
public class BUPanelService implements BUPluginLifecycle {
    @Inject
    private Client client;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private BUResourceService buResourceService;
    @Inject
    private OkHttpClient httpClient;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Gson gson;
    @Inject
    private ItemManager itemManager;
    @Inject
    private BUPanelService buPanelService;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private UnlockedItemsDataProvider unlockedItemsDataProvider;
    @Inject
    private GameRulesDataProvider gameRulesDataProvider;
    @Inject
    private MembersDataProvider membersDataProvider;

    private BUPanel buPanel;
    private NavigationButton panelNavigationButton;

    @Override
    public void startUp() {
        buPanel = new BUPanel(buResourceService, httpClient, client, clientThread, gson, itemManager, buPanelService, accountConfigurationService, unlockedItemsDataProvider, gameRulesDataProvider, membersDataProvider);
        panelNavigationButton = NavigationButton.builder()
                .tooltip("Bronzeman Unleashed")
                .icon(buResourceService.getIconBufferedImage())
                .priority(3)
                .panel(buPanel)
                .build();
        clientToolbar.addNavigation(panelNavigationButton);
    }

    @Override
    public void shutDown() throws Exception {
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
}
