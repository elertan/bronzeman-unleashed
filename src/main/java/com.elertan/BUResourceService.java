package com.elertan;

import com.elertan.resource.BUImageUtil;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class BUResourceService implements BUPluginLifecycle {

    private static final String ICON_FILE_PATH = "/icons/bu-icon.png";
    private static final String CHECKMARK_ICON_FILE_PATH = "/icons/bu-checkmark-icon.png";
    private static final String CONFIGURE_ICON_FILE_PATH = "/icons/bu-configure-icon.png";
    private static final String LOADING_SPINNER_FILE_PATH = "/icons/bu-loading-spinner.gif";

    @Getter private final BufferedImage iconBufferedImage = ImageUtil.loadImageResource(BUPlugin.class, ICON_FILE_PATH);
    @Getter private final BufferedImage checkmarkIconBufferedImage = ImageUtil.loadImageResource(BUPlugin.class, CHECKMARK_ICON_FILE_PATH);
    @Getter private final BufferedImage configureIconBufferedImage = ImageUtil.loadImageResource(BUPlugin.class, CONFIGURE_ICON_FILE_PATH);
    @Getter private final ImageIcon loadingSpinnerImageIcon = new ImageIcon(
        Objects.requireNonNull(BUPlugin.class.getResource(LOADING_SPINNER_FILE_PATH)));

    private final ConcurrentHashMap<Integer, Integer> itemImageModIconIdCache = new ConcurrentHashMap<>();
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ItemManager itemManager;
    @Getter private BUModIcons buModIcons;

    @Override
    public void startUp() { initializeModIcons(); }

    @Override
    public void shutDown() {}

    private void initializeModIcons() {
        IndexedSprite[] modIcons = client.getModIcons();
        if (modIcons == null) { clientThread.invokeLater(this::initializeModIcons); return; }

        BufferedImage chatIcon = BUImageUtil.resizeNearest(iconBufferedImage, 13, 13, 0, 0);
        IndexedSprite chatIconSprite = ImageUtil.getImageIndexedSprite(chatIcon, client);
        int chatIconId = modIcons.length;
        IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
        newModIcons[chatIconId] = chatIconSprite;
        client.setModIcons(newModIcons);
        this.buModIcons = new BUModIcons(chatIconId);
        log.debug("BUResourceService: mod icons and sprites initialized");
    }

    public CompletableFuture<Integer> getOrSetupItemImageModIconId(int itemId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Integer cached = itemImageModIconIdCache.get(itemId);
        if (cached != null) { future.complete(cached); return future; }

        clientThread.invokeLater(() -> {
            AsyncBufferedImage asyncImg = itemManager.getImage(itemId);
            asyncImg.onLoaded(() -> {
                BufferedImage resized = BUImageUtil.resizeNearest(asyncImg, 14, 14, 0, 0);
                IndexedSprite sprite = ImageUtil.getImageIndexedSprite(resized, client);
                sprite.setOffsetX(1);
                sprite.setOffsetY(2);
                IndexedSprite[] icons = client.getModIcons();
                int idx = icons.length;
                IndexedSprite[] expanded = Arrays.copyOf(icons, idx + 1);
                expanded[idx] = sprite;
                client.setModIcons(expanded);
                future.complete(idx);
            });
        });
        return future;
    }

    @Value
    public static class BUModIcons { int chatIconId; }
}
