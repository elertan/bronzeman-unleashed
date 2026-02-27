package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.panel.BUPanel;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@Singleton
public class BUPanelService implements BUPluginLifecycle {
    @Inject private ClientToolbar clientToolbar;
    @Inject private BUResourceService buResourceService;
    @Inject private AccountConfigurationService accountConfigurationService;
    @Inject private BUPanel.Factory buPanelFactory;

    private BUPanel buPanel;
    private NavigationButton panelNavigationButton;
    private Subscription accountConfigSubscription;

    @Override
    public void startUp() {
        buPanel = buPanelFactory.create();
        panelNavigationButton = NavigationButton.builder()
            .tooltip("Bronzeman Unleashed")
            .icon(buResourceService.getIconBufferedImage())
            .priority(3).panel(buPanel).build();
        clientToolbar.addNavigation(panelNavigationButton);
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::onAccountConfigChanged);
    }

    @Override
    public void shutDown() throws Exception {
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
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

    private void onAccountConfigChanged(AccountConfiguration config) {
        boolean shouldOpen = false;
        try { shouldOpen = accountConfigurationService.isCurrentAccountAutoOpenAccountConfigurationEnabled(); }
        catch (Exception ignored) {}
        if (config == null && shouldOpen) openPanel();
    }
}
