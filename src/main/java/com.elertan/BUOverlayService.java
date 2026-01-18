package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.overlays.ItemUnlockOverlay;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@Singleton
public class BUOverlayService implements BUPluginLifecycle {

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemUnlockOverlay itemUnlockOverlay;
    @Inject
    private AccountConfigurationService accountConfigurationService;

    private Subscription accountConfigSubscription;

    @Override
    public void startUp() throws Exception {
        overlayManager.add(itemUnlockOverlay);

        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }

        itemUnlockOverlay.clear();
        overlayManager.remove(itemUnlockOverlay);
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            itemUnlockOverlay.clear();
        }
    }
}
