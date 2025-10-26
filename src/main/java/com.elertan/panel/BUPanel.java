package com.elertan.panel;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPanelService;
import com.elertan.BUResourceService;
import com.elertan.data.GameRulesDataProvider;
import com.elertan.data.MembersDataProvider;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.AccountConfiguration;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import okhttp3.OkHttpClient;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class BUPanel extends PluginPanel implements AutoCloseable {
    private enum ViewState {
        WAIT_FOR_LOGIN,
        ACCOUNT_CONFIGURATION,
        READY
    }

    private final BUResourceService buResourceService;
    private final OkHttpClient httpClient;
    private final Client client;
    private final ClientThread clientThread;
    private final Gson gson;
    private final ItemManager itemManager;
    private final BUPanelService buPanelService;
    private final AccountConfigurationService accountConfigurationService;
    private final UnlockedItemsDataProvider unlockedItemsDataProvider;
    private final GameRulesDataProvider gameRulesDataProvider;
    private final MembersDataProvider membersDataProvider;

    private final CardLayout layout = new CardLayout();
    private final Map<ViewState, JPanel> views = new EnumMap<>(ViewState.class);

    private ViewState viewState;

    public BUPanel(BUResourceService buResourceService, OkHttpClient httpClient, Client client, ClientThread clientThread, Gson gson, ItemManager itemManager, BUPanelService buPanelService, AccountConfigurationService accountConfigurationService, UnlockedItemsDataProvider unlockedItemsDataProvider, GameRulesDataProvider gameRulesDataProvider, MembersDataProvider membersDataProvider) {
        super(false);

        this.buResourceService = buResourceService;
        this.httpClient = httpClient;
        this.client = client;
        this.clientThread = clientThread;
        this.gson = gson;
        this.itemManager = itemManager;
        this.buPanelService = buPanelService;
        this.accountConfigurationService = accountConfigurationService;
        this.unlockedItemsDataProvider = unlockedItemsDataProvider;
        this.gameRulesDataProvider = gameRulesDataProvider;
        this.membersDataProvider = membersDataProvider;

        setLayout(layout);
        setViewState(ViewState.WAIT_FOR_LOGIN);

        accountConfigurationService.addCurrentAccountConfigurationChangeListener(this::currentAccountConfigurationChangeListener);
    }

    private void setViewState(ViewState state) {
        viewState = state;

        JPanel view = views.computeIfAbsent(state, this::buildView); // lazy
        String key = state.name();
        if (view.getParent() == null) {
            add(view, key);
        }
        layout.show(this, key);
        revalidate();
        repaint();
    }

    private JPanel buildView(ViewState state) {
        switch (state) {
            case WAIT_FOR_LOGIN:
                return new WaitForLoginPanel(buResourceService);
            case ACCOUNT_CONFIGURATION:
                return new AccountConfigurationPanel(httpClient, clientThread, gson, buPanelService, accountConfigurationService, gameRulesDataProvider);
            case READY:
                return new MainPanel(buResourceService, itemManager, unlockedItemsDataProvider, membersDataProvider);
        }

        throw new IllegalStateException("Unexpected build view state: " + state);
    }

    private void currentAccountConfigurationChangeListener(AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            setViewState(ViewState.ACCOUNT_CONFIGURATION);
            if (accountConfigurationService.isCurrentAccountAutoOpenAccountConfigurationEnabled()) {
                buPanelService.openPanel();
            }
        } else {
            setViewState(ViewState.READY);
        }
    }

    @Override
    public void close() throws Exception {
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(this::currentAccountConfigurationChangeListener);
    }
}
