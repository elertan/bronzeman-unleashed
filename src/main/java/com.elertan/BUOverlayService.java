package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.overlays.ItemUnlockOverlay;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Consumer;
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

    private Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;

    @Override
    public void startUp() throws Exception {
        overlayManager.add(itemUnlockOverlay);

        accountConfigurationService.addCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);

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
