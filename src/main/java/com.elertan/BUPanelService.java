package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.panel.BUPanel;
import com.elertan.panel.BUPanelViewModel;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

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
    private BUPanelViewModel.Factory buPanelViewModelFactory;
    @Inject
    private BUPanel.Factory buPanelFactory;

    private BUPanel buPanel;
    private NavigationButton panelNavigationButton;

    private final Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;

    @Override
    public void startUp() {
        buPanel = buPanelFactory.create(buPanelViewModelFactory.create());
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
