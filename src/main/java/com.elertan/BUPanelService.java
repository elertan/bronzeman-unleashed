package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.panel.BUPanel;
import com.elertan.panel.BUPanelViewModel;
import com.elertan.remote.StorageService;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

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
    private StorageService storageService;

    @Inject
    private BUPanelViewModel.Factory buPanelViewModelFactory;
    @Inject
    private BUPanel.Factory buPanelFactory;

    private BUPanel buPanel;
    private NavigationButton panelNavigationButton;
    private Subscription accountConfigSubscription;
    private Subscription localProgressOpenFailureSubscription;

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

        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::currentAccountConfigurationChangeListener);
        localProgressOpenFailureSubscription = storageService.getLocalProgressOpenFailure()
            .subscribeImmediate((failure, __) -> {
                if (failure != null) {
                    onLocalProgressOpenFailure(failure);
                }
            });
    }

    @Override
    public void shutDown() throws Exception {
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
        if (localProgressOpenFailureSubscription != null) {
            localProgressOpenFailureSubscription.dispose();
            localProgressOpenFailureSubscription = null;
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

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        boolean shouldOpenPanel = false;
        try {
            shouldOpenPanel = accountConfigurationService.isCurrentAccountAutoOpenAccountConfigurationEnabled();
        } catch (Exception ignored) {
        }
        if (accountConfiguration == null && shouldOpenPanel) {
            openPanel();
        }
    }

    private void onLocalProgressOpenFailure(StorageService.LocalProgressOpenFailure failure) {
        // Drop back to setup before showing the error so the panel is not left bound to an
        // unusable local session.
        log.error("Could not open local progress", failure.getCause());
        accountConfigurationService.setCurrentAccountConfiguration(null);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            null,
            "Your local progress could not be read.\n"
                + "This can happen if the files are invalid or from an unsupported format.\n"
                + "Please start setup again from this panel.",
            "Could not open local progress",
            JOptionPane.ERROR_MESSAGE
        ));
    }
}
