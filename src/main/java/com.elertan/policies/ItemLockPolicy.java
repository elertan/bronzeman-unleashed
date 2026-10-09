package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.ItemLockService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.PolicyService;
import com.elertan.WorldTypeService;
import com.elertan.itemlock.StartingItemsLedger;
import com.elertan.itemlock.WithdrawAmountLimiter;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.NumberFormat;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;

/**
 * Limits bank withdraws of an existing account to what it got after it was counted, and keeps
 * locked items in storage that is not counted.
 */
@Singleton
public class ItemLockPolicy extends PolicyBase {

    private static final int INVENTORY_SIZE = 28;
    // Storage that is not counted: it may still hold items from before counting.
    private static final Set<Integer> UNCOUNTED_STORAGE_INTERFACES = ImmutableSet.of(
        InterfaceID.SEED_VAULT,
        InterfaceID.FARMING_TOOLS, // tool leprechaun
        InterfaceID.POH_COSTUMES // POH costume room storage
    );
    private static final Pattern TAKE_OUT_OPTION = Pattern.compile("^(withdraw|remove|take)\\b.*",
        Pattern.CASE_INSENSITIVE);

    @Inject
    private Client client;
    @Inject
    private ItemLockService itemLockService;
    @Inject
    private WithdrawAmountLimiter withdrawAmountLimiter;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ItemUnlockService itemUnlockService;

    @Inject
    public ItemLockPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService) {
        super(accountConfigurationService, gameRulesService, policyService, worldTypeService);
    }

    @Override
    public void shutDown() throws Exception {
        withdrawAmountLimiter.clear();
        super.shutDown();
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        // Any new click ends a pending Withdraw-X (for example Escape, then Deposit-X).
        withdrawAmountLimiter.clear();

        Widget widget = event.getWidget();
        if (widget == null || !itemLockService.isLockingItems() || !worldTypeService.isCurrentWorldSupported()) {
            return;
        }
        if (itemLockService.isLoading()) {
            blockWhileLoading(event, widget);
            return;
        }
        if (!itemLockService.isCounted()) {
            // Before counting, everything is still allowed: it all gets counted.
            return;
        }
        if (isUncountedStorage(widget)) {
            blockLockedItemFromUncountedStorage(event);
            return;
        }
        if (widget.getId() != InterfaceID.Bankmain.ITEMS) {
            return;
        }

        int itemId = event.getItemId();
        long bankQuantity = widget.getItemQuantity();
        long requested = StartingItemsLedger.requestedAmount(event.getMenuOption(), bankQuantity);
        if (requested == StartingItemsLedger.NOT_A_WITHDRAW || itemId <= 0) {
            return;
        }

        long room = itemLockService.withdrawRoom(itemId);
        if (requested == StartingItemsLedger.WITHDRAW_X) {
            if (room <= 0) {
                event.consume();
                sendBlockedMessage(itemId, room);
                return;
            }
            if (room < bankQuantity) {
                withdrawAmountLimiter.limitNextPrompt(room);
            }
            return;
        }

        // Compare with what the game really takes out. For example Withdraw-All of 100 sharks
        // with 18 free slots takes out 18, which is fine with 20 usable.
        long actual = StartingItemsLedger.actualWithdrawAmount(
            requested, bankQuantity, freeInventorySlots(), takesOneSlotEach(itemId));
        if (actual > room) {
            event.consume();
            sendBlockedMessage(itemId, room);
        }
    }

    /**
     * In storage that is not counted, an item the group has not unlocked can only be from before
     * counting (anything gained later passes the inventory and unlocks), so it stays there.
     * Without this, taking it out would unlock it for the whole group.
     */
    private void blockLockedItemFromUncountedStorage(MenuOptionClicked event) {
        int itemId = event.getItemId();
        if (itemId <= 0 || !TAKE_OUT_OPTION.matcher(event.getMenuOption()).matches()) {
            return;
        }
        if (!itemUnlockService.canEverUnlock(itemId)) {
            // It can never unlock (excluded or untradeable), so taking it out unlocks nothing.
            return;
        }
        boolean isUnlocked;
        try {
            isUnlocked = itemUnlockService.hasUnlockedItem(itemId);
        } catch (Exception e) {
            // Unlocks not loaded yet: fail closed.
            isUnlocked = false;
        }
        if (!isUnlocked) {
            event.consume();
            sendBlockedMessage(itemId, 0);
        }
    }

    /**
     * While the locked items load, bank withdraws wait. Otherwise a locked item could reach the
     * inventory and unlock once loading is done. Usually a second.
     */
    private void blockWhileLoading(MenuOptionClicked event, Widget widget) {
        boolean isWithdraw = widget.getId() == InterfaceID.Bankmain.ITEMS
            && StartingItemsLedger.requestedAmount(event.getMenuOption(), 0) != StartingItemsLedger.NOT_A_WITHDRAW;
        if (isWithdraw || isUncountedStorage(widget) && TAKE_OUT_OPTION.matcher(event.getMenuOption()).matches()) {
            event.consume();
            buChatService.sendRestrictionMessage(MessageKey.STILL_LOADING_PLEASE_WAIT);
        }
    }

    private static boolean isUncountedStorage(Widget widget) {
        int widgetId = widget.getId();
        return UNCOUNTED_STORAGE_INTERFACES.contains(WidgetUtil.componentToInterface(widgetId))
            || widgetId == InterfaceID.Bankmain.POTIONSTORE_ITEMS;
    }

    public void onScriptPreFired(ScriptPreFired event) {
        withdrawAmountLimiter.onScriptPreFired(event);
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.BANKMAIN) {
            withdrawAmountLimiter.clear();
        }
    }

    private int freeInventorySlots() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        return inventory == null ? INVENTORY_SIZE : Math.max(0, INVENTORY_SIZE - inventory.count());
    }

    /** Non-stackable items take one slot each, unless they are withdrawn as notes. */
    private boolean takesOneSlotEach(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item.isStackable()) {
            return false;
        }
        boolean withdrawsAsNote = client.getVarbitValue(VarbitID.BANK_WITHDRAWNOTES) == 1;
        boolean canBeNoted = item.getNote() == -1 && item.getLinkedNoteId() != -1;
        return !(withdrawsAsNote && canBeNoted);
    }

    /** Tells the player why a withdraw is blocked. */
    private void sendBlockedMessage(int itemId, long room) {
        String itemName = client.getItemDefinition(itemId).getName();
        if (room > 0) {
            String before = "You can withdraw " + NumberFormat.getIntegerInstance().format(room) + " more ";
            buChatService.sendItemRestrictionMessage(itemId, before, itemName, ". The rest is locked.");
        } else {
            buChatService.sendItemRestrictionMessage(
                itemId, "", itemName, " is locked: you owned it before Bronzeman.");
        }
    }
}
