package com.elertan.overlays;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginConfig;
import com.elertan.BUResourceService;
import com.elertan.MemberService;
import com.elertan.MinigameService;
import com.elertan.data.MembersDataProvider;
import com.elertan.models.Member;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
public class ItemUnlockOverlay extends Overlay {

    // Frame + layout
    private static final int WIDTH = 250;
    private static final int HEIGHT = 70;
    private static final int ACQUIRED_BY_HEIGHT = 10;

    // Timings (ms)
//    private static final int DISPLAY_TIME_MS = 1250; // per-item dwell
//    private static final int FADE_TIME_MS = 800;     // frame open/close split
    private static final int SWAP_TIME_MS = 350;     // crossfade between items (image+name only)

    private static final String TITLE = "Item Unlocked";
    // Queue + items
    private final ConcurrentLinkedQueue<UnlockToast> queue = new ConcurrentLinkedQueue<>();
    @Inject
    private ItemManager itemManager;
    @Inject
    private BUPluginConfig config;
    @Inject
    private BUResourceService buResourceService;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private MembersDataProvider membersDataProvider;
    @Inject
    private MemberService memberService;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private MinigameService minigameService;
    private UnlockToast current;
    private UnlockToast next;
    private Phase phase = Phase.IDLE;
    // Clocks
    private long overlayT0;    // frame open/close animation start
    private long itemT0;       // current item start
    private long swapT0;       // swap start
    // Session layout lock to avoid height jumps
    private int sessionFrameHeight = HEIGHT;

    @Inject
    private ItemUnlockOverlay() {
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    private static float progress(long now, long t0, int durationMs) {
        if (durationMs <= 0) {
            return 1f;
        }
        return clamp01((now - t0) / (float) durationMs);
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }

    private static long elapsed(long now, long t0) {
        return Math.max(0L, now - t0);
    }

    // ----- helpers -----

    public void enqueueShowUnlock(int itemId, long acquiredByAccountHash, Integer droppedByNPCId) {
        // We need members information for acquired by, waiting just in case
        membersDataProvider.waitUntilReady(null)
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("error waiting for members data provider to complete");
                    return;
                }

                clientThread.invokeLater(() -> {
                    AsyncBufferedImage img = itemManager.getImage(itemId, 1, false);
                    String droppedBy = null;
                    if (droppedByNPCId != null) {
                        NPCComposition npcComposition = client.getNpcDefinition(droppedByNPCId);
                        droppedBy = npcComposition.getName();
                    }
                    queue.add(new UnlockToast(itemId, acquiredByAccountHash, droppedBy, img));
                    if (phase == Phase.IDLE && current == null) {
                        current = queue.poll();
                        startOpeningSession();
                    }
                });
            });
    }

    public void clear() {
        queue.clear();
        current = null;
        next = null;
        phase = Phase.IDLE;
        overlayT0 = 0L;
        itemT0 = 0L;
        swapT0 = 0L;
        sessionFrameHeight = HEIGHT;
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!config.showUnlockOverlay()) {
            return null;
        }
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return null;
        }
        if (config.hideUnlockOverlayInMinigames() && minigameService.isInMinigameOrInstance()) {
            return null;
        }

        final long now = System.currentTimeMillis();

        // Advance state
        switch (phase) {
            case OPENING:
                if (progress(now, overlayT0, config.unlockOverlayOpenAndCloseDuration()) >= 1f) {
                    phase = Phase.SHOWING;
                    itemT0 = now;
                }
                break;
            case SHOWING:
                if (elapsed(now, itemT0) >= config.unlockOverlayItemVisibleDuration()) {
                    if (!queue.isEmpty()) {
                        next = queue.poll();
                        phase = Phase.SWAPPING;
                        swapT0 = now;
                    } else {
                        phase = Phase.CLOSING;
                        overlayT0 = now;
                    }
                }
                break;
            case SWAPPING:
                if (progress(now, swapT0, SWAP_TIME_MS) >= 1f) {
                    current = next;
                    next = null;
                    phase = Phase.SHOWING;
                    itemT0 = now;
                }
                break;
            case CLOSING:
                if (progress(now, overlayT0, config.unlockOverlayOpenAndCloseDuration()) >= 1f) {
                    phase = Phase.IDLE;
                    current = null;
                    next = null;
                    return null;
                }
                break;
            case IDLE:
                if (current == null && !queue.isEmpty()) {
                    current = queue.poll();
                    startOpeningSession();
                } else {
                    return null;
                }
                break;
        }

        // Frame open/close progress
        float openProgress;
        if (phase == Phase.OPENING) {
            openProgress = progress(now, overlayT0, config.unlockOverlayOpenAndCloseDuration());
        } else if (phase == Phase.CLOSING) {
            openProgress =
                1f - progress(now, overlayT0, config.unlockOverlayOpenAndCloseDuration());
        } else {
            openProgress = 1f;
        }
        openProgress = clamp01(openProgress);

        // Split vertical/horizontal
        float verticalProgress;
        float horizontalProgress;
        if (openProgress < 0.5f) {
            verticalProgress = openProgress / 0.5f;
            horizontalProgress = 0f;
        } else {
            verticalProgress = 1f;
            horizontalProgress = (openProgress - 0.5f) / 0.5f;
        }

        int visibleHeight = Math.round(sessionFrameHeight * verticalProgress);
        int visibleWidth = Math.round(5 + (WIDTH - 5) * horizontalProgress);
        if (visibleHeight < 1 || visibleWidth < 1) {
            return new Dimension(WIDTH, sessionFrameHeight);
        }

        int y = 40;
        int frameX = WIDTH + -visibleWidth / 2; // TOP_CENTER anchor

        g.setComposite(AlphaComposite.SrcOver);

        // Frame
        drawFrame(g, frameX, y, visibleWidth, visibleHeight);

        // Contents
        if (visibleHeight > 20 && visibleWidth > 40) {
            final float minW = WIDTH * 0.8f;
            final float minH = sessionFrameHeight * 0.8f;
            final boolean bigEnough = visibleWidth >= minW && visibleHeight >= minH;

            switch (phase) {
                case OPENING: {
                    // Delay content until frame mostly open (0.8->1 fade-in)
                    float alpha = 0f;
                    if (bigEnough) {
                        alpha = (openProgress - 0.8f) / 0.2f; // 0..1
                    }
                    alpha = clamp01(alpha);

                    if (alpha > 0f && current != null) {
                        drawTitle(g, frameX, y, visibleWidth, visibleHeight, alpha);
                        drawItemBlock(g, current, frameX, y, visibleWidth, visibleHeight, alpha);
                    }
                    break;
                }
                case SHOWING: {
                    drawTitle(g, frameX, y, visibleWidth, visibleHeight, 1f);
                    if (current != null) {
                        drawItemBlock(g, current, frameX, y, visibleWidth, visibleHeight, 1f);
                    }
                    break;
                }
                case SWAPPING: {
                    // Title remains stable (no fade between items)
                    drawTitle(g, frameX, y, visibleWidth, visibleHeight, 1f);
                    float p = clamp01(progress(now, swapT0, SWAP_TIME_MS));
                    if (current != null) {
                        drawItemBlock(g, current, frameX, y, visibleWidth, visibleHeight, 1f - p);
                    }
                    if (next != null) {
                        drawItemBlock(g, next, frameX, y, visibleWidth, visibleHeight, p);
                    }
                    break;
                }
                case CLOSING: {
                    // Mirror original behavior:
                    // while frame is 100%..80% of size, fade from 1->0; below 80% hide content.
                    if (bigEnough) {
                        float alpha = (openProgress - 0.8f) / 0.2f; // 1 at 1.0, 0 at 0.8
                        alpha = clamp01(alpha);
                        if (alpha > 0f && current != null) {
                            drawTitle(g, frameX, y, visibleWidth, visibleHeight, alpha);
                            drawItemBlock(
                                g,
                                current,
                                frameX,
                                y,
                                visibleWidth,
                                visibleHeight,
                                alpha
                            );
                        }
                    }
                    // If not bigEnough, draw nothing (content hidden while frame collapses).
                    break;
                }
                default:
                    break;
            }
        }

        return new Dimension(WIDTH, sessionFrameHeight);
    }

    private void startOpeningSession() {
        overlayT0 = System.currentTimeMillis();
        sessionFrameHeight =
            config.showAcquiredByInUnlockOverlay() ? (HEIGHT + ACQUIRED_BY_HEIGHT) : HEIGHT;
        phase = Phase.OPENING;
    }

    private void drawFrame(Graphics2D g, int x, int y, int w, int h) {
        // Outline
        g.setColor(new Color(45, 45, 45));
        g.fillRect(x, y, w, h);

        // Outer
        g.setColor(config.unlockOverlayFrameOuterColor());
        g.fillRect(x + 1, y + 1, w - 2, h - 2);

        // Inner outline
        g.setColor(new Color(45, 45, 45));
        g.fillRect(x + 5, y + 5, w - 10, h - 10);

        // Inner
        g.setColor(config.unlockOverlayFrameInnerColor());
        g.fillRect(x + 6, y + 6, w - 12, h - 12);
    }

    private void drawTitle(Graphics2D g, int frameX, int y, int visibleWidth, int visibleHeight,
        float alpha) {
        if (alpha <= 0f) {
            return;
        }

        final Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Plugin icon
        g.drawImage(buResourceService.getIconBufferedImage(), frameX + 10, y + 10, 16, 16, null);

        // Title
        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics fmBold = g.getFontMetrics();
        final String title = TITLE;
        int tx = frameX + (visibleWidth - fmBold.stringWidth(title)) / 2;
        int titleY = y + 24;
        if (titleY < y + visibleHeight - 5) {
            g.setColor(new Color(0, 0, 0));
            g.drawString(title, tx + 1, titleY + 1);

            g.setColor(new Color(255, 145, 0));
            g.drawString(title, tx, titleY);
        }

        g.setComposite(old);
    }

    private void drawItemBlock(
        Graphics2D g, UnlockToast toast, int frameX, int y,
        int visibleWidth, int visibleHeight, float alpha
    ) {
        if (alpha <= 0f) {
            return;
        }

        final Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Subtitle and item icon
        final int itemId = toast.itemId;
        final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
        final String subtitle =
            itemComposition != null ? itemComposition.getName() : "Unknown item";

        g.setFont(FontManager.getRunescapeFont());
        FontMetrics fm = g.getFontMetrics();

        int iconSize = 32;
        int textWidth = fm.stringWidth(subtitle);
        int totalBlockWidth = iconSize + 5 + textWidth;

        int blockX = frameX + (visibleWidth - totalBlockWidth) / 2;
        int iconX = blockX;

        boolean showAcquiredBy = shouldShowAcquiredBy(toast.acquiredByAccountHash);
        int iconYOffset = showAcquiredBy ? 10 : 20;
        int iconY = y + (visibleHeight + iconYOffset - iconSize) / 2;

        AsyncBufferedImage itemImage = toast.image;
        if (itemImage != null) {
            g.drawImage(itemImage, iconX, iconY, null);
        }

        int textX = iconX + iconSize + 5;
        int textY = iconY + (iconSize + fm.getAscent() - fm.getDescent()) / 2;
        if (textY < y + visibleHeight - 5) {
            g.setColor(new Color(0, 0, 0));
            g.drawString(subtitle, textX + 1, textY + 1);

            g.setColor(config.unlockOverlayItemTextColor());
            g.drawString(subtitle, textX, textY);
        }

        // "acquired by ..."
        if (showAcquiredBy) {
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics fmSmall = g.getFontMetrics();

            Member acquiredByMember = memberService.getMemberByAccountHash(toast.acquiredByAccountHash);

            final String label = "unlocked by";
            final String name = acquiredByMember.getName();

            int labelW = fmSmall.stringWidth(label);
            int spaceW = fmSmall.charWidth(' ');
            int nameW = fmSmall.stringWidth(name);

            int rightPad = 10; // keep a small margin from the right edge
            int startX = frameX + visibleWidth - (labelW + spaceW + nameW) - rightPad;
            int acquiredByY = textY + fmSmall.getAscent() + 8;

            if (acquiredByY < y + visibleHeight) {
                g.setColor(Color.GRAY);
                g.drawString(label, startX, acquiredByY);

                g.setColor(Color.LIGHT_GRAY);
                g.drawString(name, startX + labelW + spaceW, acquiredByY);
            }
        }
        g.setComposite(old);
    }

    private boolean shouldShowAcquiredBy(long acquiredByAccountHash) {
        if (!config.showAcquiredByInUnlockOverlay()) {
            return false;
        }

        if (!config.showAcquiredByInUnlockOverlayForSelf()) {
            long accountHash = client.getAccountHash();
            return !Objects.equals(accountHash, acquiredByAccountHash);
        }
        return true;
    }

    // State machine
    private enum Phase {IDLE, OPENING, SHOWING, SWAPPING, CLOSING}

    private static final class UnlockToast {

        final int itemId;
        final long acquiredByAccountHash;
        final String droppedBy;
        final AsyncBufferedImage image;

        UnlockToast(int itemId, long acquiredByAccountHash, String droppedBy,
            AsyncBufferedImage image) {
            this.itemId = itemId;
            this.acquiredByAccountHash = acquiredByAccountHash;
            this.droppedBy = droppedBy;
            this.image = image;
        }
    }
}