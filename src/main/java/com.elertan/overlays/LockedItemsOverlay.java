package com.elertan.overlays;

import com.elertan.BUResourceService;
import com.elertan.ItemLockService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Marks locked items in the bank of an existing account. Every item with a locked quantity gets a
 * bronze padlock; items that cannot be withdrawn at all also fade like placeholders, and items with
 * some usable quantity show it in bronze below the padlock.
 */
@Singleton
public class LockedItemsOverlay extends Overlay {

    // Widget opacity: 0 is opaque, 255 is invisible. An unusual value, so we only ever reset
    // the opacity that this overlay set.
    private static final int LOCKED_OPACITY = 131;
    // Between the light and base bronze of the padlock.
    private static final Color AMOUNT_COLOR = new Color(205, 140, 82);

    private final Client client;
    private final ItemLockService itemLockService;
    private final TooltipManager tooltipManager;
    private final ClientThread clientThread;
    private final BufferedImage padlock;
    // A bit smaller than the game's quantity font, so the amount stays secondary to the stack size.
    private final Font amountFont = FontManager.getRunescapeSmallFont().deriveFont(13f);
    private boolean hasFadedItems;

    @Inject
    public LockedItemsOverlay(Client client, ItemLockService itemLockService,
        TooltipManager tooltipManager, ClientThread clientThread, BUResourceService buResourceService) {
        this.client = client;
        this.itemLockService = itemLockService;
        this.tooltipManager = tooltipManager;
        this.clientThread = clientThread;
        this.padlock = buResourceService.getPadlockIconBufferedImage();
        setPosition(OverlayPosition.DYNAMIC);
        // Draw right after the bank items, so popups on top of the bank (like the incinerator
        // confirmation) cover the padlocks.
        setLayer(OverlayLayer.MANUAL);
        drawAfterLayer(InterfaceID.Bankmain.ITEMS);
    }

    /**
     * The bank resets item opacity when it rebuilds (tab switch, search). Fading right after the
     * rebuild, before the frame is drawn, prevents a flicker.
     */
    public void onScriptPostFired(ScriptPostFired event) {
        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.BANKMAIN_FINISHBUILDING || scriptId == ScriptID.BANKMAIN_SEARCH_REFRESH) {
            applyFade();
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Widget bankItems = getBankItems();
        if (bankItems == null) {
            return null;
        }
        applyFade();
        if (!isActive()) {
            return null;
        }

        // Draw only inside the bank viewport, so half-scrolled items are marked too.
        Rectangle viewport = bankItems.getBounds();
        Shape oldClip = graphics.getClip();
        graphics.clip(viewport);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Point mouse = client.getMouseCanvasPosition();

        for (Widget item : bankItems.getDynamicChildren()) {
            int starting = startingQuantity(item);
            if (starting <= 0) {
                continue;
            }
            Rectangle bounds = item.getBounds();
            if (!bounds.intersects(viewport)) {
                continue;
            }
            int itemId = item.getItemId();
            long room = itemLockService.withdrawRoom(itemId);
            // The padlock marks every item with locked items; the usable amount goes below it.
            int lockX = bounds.x + bounds.width - padlock.getWidth() - 1;
            int lockY = bounds.y + 1;
            graphics.drawImage(padlock, lockX, lockY, null);
            if (room > 0) {
                drawUsableAmount(graphics, room, lockX + padlock.getWidth() / 2, lockY + padlock.getHeight(),
                    bounds.x + bounds.width);
            }

            if (mouse != null && viewport.contains(mouse.getX(), mouse.getY())
                && bounds.contains(mouse.getX(), mouse.getY())) {
                NumberFormat format = NumberFormat.getIntegerInstance();
                tooltipManager.add(new Tooltip(
                    "Locked: " + format.format(starting)
                        + "</br>Usable: " + format.format(room)
                ));
            }
        }

        graphics.setClip(oldClip);
        return null;
    }

    /** Puts back the opacity this overlay changed, for example when the plugin stops. */
    public void resetFade() {
        clientThread.invokeLater(() -> applyFade(false));
    }

    private void applyFade() {
        applyFade(isActive());
    }

    private void applyFade(boolean isActive) {
        Widget bankItems = getBankItems();
        // New accounts never get past this check, so they pay nothing.
        if (bankItems == null || !isActive && !hasFadedItems) {
            return;
        }
        hasFadedItems = isActive;
        for (Widget item : bankItems.getDynamicChildren()) {
            if (item == null) {
                continue;
            }
            boolean isLocked = isActive && startingQuantity(item) > 0
                && itemLockService.withdrawRoom(item.getItemId()) <= 0;
            if (isLocked && item.getOpacity() != LOCKED_OPACITY) {
                item.setOpacity(LOCKED_OPACITY);
            } else if (!isLocked && item.getOpacity() == LOCKED_OPACITY) {
                item.setOpacity(0);
            }
        }
    }

    private Widget getBankItems() {
        Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (bankItems == null || bankItems.isHidden() || bankItems.getDynamicChildren() == null) {
            return null;
        }
        return bankItems;
    }

    private boolean isActive() {
        return itemLockService.getStatus().get() == ItemLockService.Status.COUNTED;
    }

    private int startingQuantity(Widget item) {
        if (item == null || item.isHidden() || item.getItemId() <= 0 || item.getItemQuantity() <= 0) {
            return 0;
        }
        return itemLockService.startingQuantity(item.getItemId());
    }

    /** Draws the amount centered on centerX, a little below top, with a black shadow. */
    private void drawUsableAmount(Graphics2D graphics, long room, int centerX, int top, int maxRight) {
        String text = QuantityFormatter.quantityToStackSize(room);
        graphics.setFont(amountFont);
        FontMetrics metrics = graphics.getFontMetrics();
        int width = metrics.stringWidth(text);
        // Centered below the padlock, but never past the right edge of the item.
        int x = Math.min(centerX - width / 2, maxRight - width);
        // Digits are about 8/11 of the ascent in this font; leave a 3px gap below the padlock.
        int y = top + 3 + (int) Math.ceil(metrics.getAscent() * 8 / 11.0);
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(AMOUNT_COLOR);
        graphics.drawString(text, x, y);
    }
}
