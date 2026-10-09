package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.ItemLockService;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.WorldTypeService;
import com.elertan.itemlock.WithdrawAmounts;
import com.elertan.itemlock.WithdrawAmountLimiter;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.utils.TextUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import net.runelite.client.input.KeyManager;

/**
 * Limits bank withdraws of an existing account to what it got after it was counted.
 */
@Singleton
public class ItemLockPolicy extends PolicyBase {

    private static final int INVENTORY_SIZE = 28;

    @Inject
    private Client client;
    @Inject
    private ItemLockService itemLockService;
    @Inject
    private WithdrawAmountLimiter withdrawAmountLimiter;
    @Inject
    private BUChatService buChatService;
    @Inject
    private KeyManager keyManager;

    @Inject
    public ItemLockPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService) {
        super(accountConfigurationService, gameRulesService, policyService, worldTypeService);
    }

    @Override
    public void startUp() throws Exception {
        super.startUp();
        keyManager.registerKeyListener(withdrawAmountLimiter);
    }

    @Override
    public void shutDown() throws Exception {
        keyManager.unregisterKeyListener(withdrawAmountLimiter);
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
        if (widget.getId() != InterfaceID.Bankmain.ITEMS) {
            return;
        }

        int itemId = event.getItemId();
        int bankQuantity = widget.getItemQuantity();
        int requested = WithdrawAmounts.requested(event.getMenuOption(), bankQuantity);
        if (requested == WithdrawAmounts.NOT_A_WITHDRAW || itemId <= 0) {
            return;
        }

        int room = itemLockService.withdrawRoom(itemId);
        if (requested == WithdrawAmounts.WITHDRAW_X) {
            if (room <= 0) {
                event.consume();
                sendBlockedMessage(itemId, room);
                return;
            }
            if (room < bankQuantity) {
                withdrawAmountLimiter.limitNextPrompt(room, typed -> sendWithdrawXBlockedMessage(itemId, typed, room));
            }
            return;
        }

        // For example Withdraw-All of 100 sharks with 18 free slots takes out 18.
        int actual = WithdrawAmounts.actual(requested, bankQuantity, freeInventorySlots(), takesOneSlotEach(itemId));
        if (actual > room) {
            event.consume();
            sendBlockedMessage(itemId, room);
        } else if (!event.isConsumed()) {
            itemLockService.addPendingWithdraw(itemId, actual);
        }
    }

    /**
     * While the locked items load, bank withdraws wait. Otherwise a locked item could reach the
     * inventory and unlock once loading is done. Usually a second.
     */
    private void blockWhileLoading(MenuOptionClicked event, Widget widget) {
        boolean isWithdraw = widget.getId() == InterfaceID.Bankmain.ITEMS
            && WithdrawAmounts.requested(event.getMenuOption(), 0) != WithdrawAmounts.NOT_A_WITHDRAW;
        if (isWithdraw) {
            event.consume();
            buChatService.sendRestrictionMessage(MessageKey.STILL_LOADING_PLEASE_WAIT);
        }
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

    private void sendWithdrawXBlockedMessage(int itemId, long typed, int room) {
        buChatService.sendItemRestrictionMessage(
            itemId,
            "You can't withdraw " + TextUtils.formatStackSize(typed) + " ",
            client.getItemDefinition(itemId).getName(),
            " because you only have " + TextUtils.formatStackSize(room) + " available. The rest is locked."
        );
    }

    /** Tells the player why a withdraw is blocked. */
    private void sendBlockedMessage(int itemId, int room) {
        String itemName = client.getItemDefinition(itemId).getName();
        if (room > 0) {
            String before = "You can withdraw " + TextUtils.formatStackSize(room) + " more ";
            buChatService.sendItemRestrictionMessage(itemId, before, itemName, ". The rest is locked.");
        } else {
            buChatService.sendItemRestrictionMessage(
                itemId, "", itemName, " is locked: you owned it before Bronzeman.");
        }
    }
}
