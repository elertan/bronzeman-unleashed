package com.elertan;

import com.elertan.resource.BUImageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.SpritePixels;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Singleton
public class BUResourceService implements BUPluginLifecycle {
    private static final String ICON_FILE_PATH = "/icon.png";
    private static final String CHECKMARK_ICON_FILE_PATH = "/checkmark-icon.png";
    private static final String CONFIGURE_ICON_FILE_PATH = "/configure-icon.png";
    private static final String LOADING_SPINNER_FILE_PATH = "/loading-spinner.gif";

    public static class BUModIcons {
        @Getter
        final private int chatIconId;

        public BUModIcons(int chatIconId) {
            this.chatIconId = chatIconId;
        }
    }

    // Some arbitrary offset that does not clash with other sprite ids other plugins or RuneLite itself provides
    private static final int SPRITE_ID_OFFSET = 1337_42_69;
    public static class BUSprites {
        @Getter
        final private int iconId;
        @Getter
        final private int checkmarkId;

        public BUSprites(int iconId, int checkmarkId) {
            this.iconId = iconId;
            this.checkmarkId = checkmarkId;
        }
    }

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;

    @Getter
    private final BufferedImage iconBufferedImage = ImageUtil.loadImageResource(BUPlugin.class, ICON_FILE_PATH);

    @Getter
    private final BufferedImage checkmarkIconBufferedImage = ImageUtil.loadImageResource(BUPlugin.class, CHECKMARK_ICON_FILE_PATH);

    @Getter
    private final BufferedImage configureIconBufferedImage = ImageUtil.loadImageResource(BUPlugin.class, CONFIGURE_ICON_FILE_PATH);

    @Getter
    private final ImageIcon loadingSpinnerImageIcon = new ImageIcon(Objects.requireNonNull(BUPlugin.class.getResource(LOADING_SPINNER_FILE_PATH)));

    @Getter
    private BUModIcons buModIcons;
    @Getter
    private BUSprites buSprites;

    @Override
    public void startUp() {
        this.initializeModIconsAndSprites();
    }

    @Override
    public void shutDown() {

    }

    private void initializeModIconsAndSprites() {
        IndexedSprite[] modIcons = client.getModIcons();
        if (modIcons == null) {
            // Retry later when is initialized
            clientThread.invokeLater(this::initializeModIconsAndSprites);
            return;
        }

        // Mod icons
        BufferedImage chatIcon = BUImageUtil.resizeNearest(iconBufferedImage, 13, 13);
        IndexedSprite chatIconSprite = ImageUtil.getImageIndexedSprite(chatIcon, client);

        int chatIconId = modIcons.length;

        IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
        newModIcons[chatIconId] = chatIconSprite;
        client.setModIcons(newModIcons);

        this.buModIcons = new BUModIcons(chatIconId);

        // Sprites
        Map<Integer, SpritePixels> spriteOverrides = client.getSpriteOverrides();

        int iconSpriteId = SPRITE_ID_OFFSET;
        SpritePixels iconSpritePixels = ImageUtil.getImageSpritePixels(iconBufferedImage, client);
        spriteOverrides.put(iconSpriteId, iconSpritePixels);

        int checkmarkIconSpriteId = iconSpriteId + 1;
        SpritePixels checkmarkIconSpritePixels = ImageUtil.getImageSpritePixels(checkmarkIconBufferedImage, client);
        spriteOverrides.put(checkmarkIconSpriteId, checkmarkIconSpritePixels);

        this.buSprites = new BUSprites(iconSpriteId, checkmarkIconSpriteId);

        log.info("BUResourceService: mod icons and sprites initialized");
    }

    private void removeModIconsAndSprites() {
        // TODO: Remove mod icons and sprites
//        Map<Integer, SpritePixels> spriteOverrides = client.getSpriteOverrides();
//        spriteOverrides.remove(buSprites.getIconId());
//        spriteOverrides.remove(buSprites.getCheckmarkId());
//
//        IndexedSprite[] modIcons = client.getModIcons();
//        if (modIcons != null && buModIcons != null) {
//            int idx = buModIcons.getChatIconId();
//            if (idx >= 0 && idx < modIcons.length) {
//                IndexedSprite[] trimmed = Arrays.copyOf(modIcons, modIcons.length - 1);
//                client.setModIcons(trimmed);
//            }
//        }
    }
}
