package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.overlays.ItemUnlockOverlay;
import com.elertan.overlays.LockedItemsOverlay;
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
    private LockedItemsOverlay lockedItemsOverlay;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private ItemLockService itemLockService;

    private Subscription accountConfigSubscription;
    private Subscription itemLockStatusSubscription;

    @Override
    public void startUp() throws Exception {
        overlayManager.add(itemUnlockOverlay);
        overlayManager.add(lockedItemsOverlay);

        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::currentAccountConfigurationChangeListener);
        itemLockStatusSubscription = itemLockService.getStatus()
            .subscribe((status, old) -> lockedItemsOverlay.refreshFade());
    }

    @Override
    public void shutDown() throws Exception {
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
        if (itemLockStatusSubscription != null) {
            itemLockStatusSubscription.dispose();
            itemLockStatusSubscription = null;
        }

        itemUnlockOverlay.clear();
        overlayManager.remove(itemUnlockOverlay);
        overlayManager.remove(lockedItemsOverlay);
        lockedItemsOverlay.resetFade();
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            itemUnlockOverlay.clear();
        }
    }
}
