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
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
public class ItemUnlockOverlay extends Overlay {

    private static final int WIDTH = 250;
    private static final int HEIGHT = 70;
    private static final int ACQUIRED_BY_HEIGHT = 10;
    private static final int SWAP_TIME_MS = 350;
    private static final String TITLE = "Item Unlocked";
    private static final Color TITLE_COLOR = new Color(255, 145, 0);
    private static final Color OUTLINE_COLOR = new Color(45, 45, 45);

    private final ConcurrentLinkedQueue<UnlockToast> queue = new ConcurrentLinkedQueue<>();
    @Inject private ItemManager itemManager;
    @Inject private RuneLiteConfig runeLiteConfig;
    @Inject private BUPluginConfig config;
    @Inject private BUResourceService buResourceService;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private MembersDataProvider membersDataProvider;
    @Inject private MemberService memberService;
    @Inject private AccountConfigurationService accountConfigurationService;
    @Inject private MinigameService minigameService;
    private UnlockToast current;
    private UnlockToast next;
    private Phase phase = Phase.IDLE;
    private long overlayT0, itemT0, swapT0;
    private int sessionFrameHeight = HEIGHT;

    @Inject
    private ItemUnlockOverlay() {
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    private static float progress(long now, long t0, int durationMs) {
        return durationMs <= 0 ? 1f : clamp01((now - t0) / (float) durationMs);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    public void enqueueShowUnlock(int itemId, long acquiredByAccountHash, Integer droppedByNPCId) {
        membersDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) { log.error("error waiting for members data provider"); return; }
            clientThread.invokeLater(() -> {
                AsyncBufferedImage img = itemManager.getImage(itemId, 1, false);
                String droppedBy = droppedByNPCId != null
                    ? client.getNpcDefinition(droppedByNPCId).getName() : null;
                queue.add(new UnlockToast(itemId, acquiredByAccountHash, droppedBy, img));
                if (phase == Phase.IDLE && current == null) {
                    current = queue.poll();
                    startOpeningSession();
                }
            });
        });
    }

    public void clear() {
        queue.clear(); current = null; next = null;
        phase = Phase.IDLE; overlayT0 = 0L; itemT0 = 0L; swapT0 = 0L;
        sessionFrameHeight = HEIGHT;
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!config.showUnlockOverlay() || !accountConfigurationService.isBronzemanEnabled()) return null;
        if (config.hideUnlockOverlayInMinigames() && minigameService.isInMinigameOrInstance()) return null;

        final long now = System.currentTimeMillis();
        advanceStateMachine(now);
        if (phase == Phase.IDLE) return null;

        float openProg = computeOpenProgress(now);
        float vp = openProg < 0.5f ? openProg / 0.5f : 1f;
        float hp = openProg < 0.5f ? 0f : (openProg - 0.5f) / 0.5f;
        int visH = Math.round(sessionFrameHeight * vp), visW = Math.round(5 + (WIDTH - 5) * hp);
        if (visH < 1 || visW < 1) return new Dimension(WIDTH, sessionFrameHeight);

        // Draw centered within the overlay's drag rectangle. The overlay
        // system already positions this rectangle, so avoid extra offsets
        // that would desync the popup from the yellow outline.
        int y = 0;
        int frameX = (WIDTH - visW) / 2;
        g.setComposite(AlphaComposite.SrcOver);
        drawFrame(g, frameX, y, visW, visH);

        if (visH > 20 && visW > 40) {
            boolean big = visW >= WIDTH * 0.8f && visH >= sessionFrameHeight * 0.8f;
            float alpha = (phase == Phase.OPENING || phase == Phase.CLOSING)
                ? (big ? clamp01((openProg - 0.8f) / 0.2f) : 0f) : 1f;
            if (alpha > 0f) {
                drawTitle(g, frameX, y, visW, visH, alpha);
                if (phase == Phase.SWAPPING) {
                    float p = clamp01(progress(now, swapT0, SWAP_TIME_MS));
                    if (current != null) drawItemBlock(g, current, frameX, y, visW, visH, alpha * (1f - p));
                    if (next != null) drawItemBlock(g, next, frameX, y, visW, visH, alpha * p);
                } else if (current != null) {
                    drawItemBlock(g, current, frameX, y, visW, visH, alpha);
                }
            }
        }
        return new Dimension(WIDTH, sessionFrameHeight);
    }

    private void advanceStateMachine(long now) {
        int fadeDur = config.unlockOverlayOpenAndCloseDuration();
        switch (phase) {
            case OPENING:
                if (progress(now, overlayT0, fadeDur) >= 1f) { phase = Phase.SHOWING; itemT0 = now; }
                break;
            case SHOWING:
                if (now - itemT0 >= config.unlockOverlayItemVisibleDuration()) {
                    if (!queue.isEmpty()) { next = queue.poll(); phase = Phase.SWAPPING; swapT0 = now; }
                    else { phase = Phase.CLOSING; overlayT0 = now; }
                }
                break;
            case SWAPPING:
                if (progress(now, swapT0, SWAP_TIME_MS) >= 1f) {
                    current = next; next = null; phase = Phase.SHOWING; itemT0 = now;
                }
                break;
            case CLOSING:
                if (progress(now, overlayT0, fadeDur) >= 1f) { phase = Phase.IDLE; current = null; next = null; }
                break;
            case IDLE:
                if (current == null && !queue.isEmpty()) { current = queue.poll(); startOpeningSession(); }
                break;
        }
    }

    private float computeOpenProgress(long now) {
        int dur = config.unlockOverlayOpenAndCloseDuration();
        if (phase == Phase.OPENING) return clamp01(progress(now, overlayT0, dur));
        if (phase == Phase.CLOSING) return clamp01(1f - progress(now, overlayT0, dur));
        return 1f;
    }

    private void startOpeningSession() {
        overlayT0 = System.currentTimeMillis();
        sessionFrameHeight = config.showAcquiredByInUnlockOverlay() ? HEIGHT + ACQUIRED_BY_HEIGHT : HEIGHT;
        phase = Phase.OPENING;
    }

    private void drawFrame(Graphics2D g, int x, int y, int w, int h) {
        Color bg = runeLiteConfig.overlayBackgroundColor();
        Color border = config.unlockOverlayFrameBorderColor();
        if (border == null) border = bg.brighter();
        g.setColor(OUTLINE_COLOR); g.fillRect(x, y, w, h);
        g.setColor(border); g.fillRect(x + 1, y + 1, w - 2, h - 2);
        g.setColor(OUTLINE_COLOR); g.fillRect(x + 5, y + 5, w - 10, h - 10);
        g.setColor(bg); g.fillRect(x + 6, y + 6, w - 12, h - 12);
    }

    private void drawShadowedString(Graphics2D g, String text, int x, int y, Color fg) {
        g.setColor(Color.BLACK); g.drawString(text, x + 1, y + 1);
        g.setColor(fg); g.drawString(text, x, y);
    }

    private void drawTitle(Graphics2D g, int fx, int y, int vw, int vh, float alpha) {
        if (alpha <= 0f) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(buResourceService.getIconBufferedImage(), fx + 10, y + 10, 16, 16, null);
        g.setFont(FontManager.getRunescapeBoldFont());
        int tx = fx + (vw - g.getFontMetrics().stringWidth(TITLE)) / 2;
        int titleY = y + 24;
        if (titleY < y + vh - 5) drawShadowedString(g, TITLE, tx, titleY, TITLE_COLOR);
        g.setComposite(old);
    }

    private void drawItemBlock(Graphics2D g, UnlockToast toast, int fx, int y,
        int vw, int vh, float alpha) {
        if (alpha <= 0f) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        ItemComposition ic = itemManager.getItemComposition(toast.itemId);
        String subtitle = ic != null ? ic.getName() : "Unknown item";
        g.setFont(FontManager.getRunescapeFont());
        FontMetrics fm = g.getFontMetrics();
        int iconSz = 32;
        int blockX = fx + (vw - iconSz - 5 - fm.stringWidth(subtitle)) / 2;
        boolean showAcq = shouldShowAcquiredBy(toast.acquiredByAccountHash);
        int iconY = y + (vh + (showAcq ? 10 : 20) - iconSz) / 2;
        if (toast.image != null) g.drawImage(toast.image, blockX, iconY, null);

        int textX = blockX + iconSz + 5;
        int textY = iconY + (iconSz + fm.getAscent() - fm.getDescent()) / 2;
        if (textY < y + vh - 5) drawShadowedString(g, subtitle, textX, textY, config.unlockOverlayItemTextColor());

        if (showAcq) {
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics fs = g.getFontMetrics();
            Member m = memberService.getMemberByAccountHash(toast.acquiredByAccountHash);
            String label = "unlocked by", name = m.getName();
            int lw = fs.stringWidth(label), sw = fs.charWidth(' '), nw = fs.stringWidth(name);
            int sx = fx + vw - (lw + sw + nw) - 10;
            int ay = textY + fs.getAscent() + 8;
            if (ay < y + vh) {
                g.setColor(Color.GRAY); g.drawString(label, sx, ay);
                g.setColor(Color.LIGHT_GRAY); g.drawString(name, sx + lw + sw, ay);
            }
        }
        g.setComposite(old);
    }

    private boolean shouldShowAcquiredBy(long hash) {
        if (!config.showAcquiredByInUnlockOverlay()) return false;
        if (!config.showAcquiredByInUnlockOverlayForSelf()) return !Objects.equals(client.getAccountHash(), hash);
        return true;
    }

    private enum Phase { IDLE, OPENING, SHOWING, SWAPPING, CLOSING }

    private static final class UnlockToast {
        final int itemId;
        final long acquiredByAccountHash;
        final String droppedBy;
        final AsyncBufferedImage image;
        UnlockToast(int itemId, long hash, String droppedBy, AsyncBufferedImage image) {
            this.itemId = itemId; this.acquiredByAccountHash = hash;
            this.droppedBy = droppedBy; this.image = image;
        }
    }
}
