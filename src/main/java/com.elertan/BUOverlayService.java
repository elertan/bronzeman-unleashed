package com.elertan;

import com.elertan.overlays.ItemUnlockOverlay;
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

    @Override
    public void startUp() throws Exception {
        overlayManager.add(itemUnlockOverlay);
    }

    @Override
    public void shutDown() throws Exception {
        overlayManager.remove(itemUnlockOverlay);
    }
}
