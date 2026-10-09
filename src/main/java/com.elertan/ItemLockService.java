package com.elertan;

import com.elertan.data.AbstractDataProvider;
import com.elertan.data.MembersDataProvider;
import com.elertan.data.StartingItemsDataProvider;
import com.elertan.itemlock.StartingItemsLedger;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.elertan.models.StartMode;
import com.elertan.models.StartingItems;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Item locking for existing accounts.
 *
 * <p>Counting needs an empty inventory and equipment, and locked items can never be withdrawn. So
 * locked items only exist in the bank, and everything the player carries was earned. The bank is
 * therefore not an unlock source, and a withdraw may only take what is above the locked quantity.
 *
 * <p>Known gaps, also shown during setup: storage that is not counted, item charges, rune pouches
 * and containers filled from the bank, and the POH servant fetching from the bank.
 */
@Slf4j
@Singleton
public class ItemLockService implements BUPluginLifecycle {

    private static final int REMINDER_INTERVAL_TICKS = 1000; // about 10 minutes
    // A withdraw the server did not do (for example a full inventory) stops counting after this.
    private static final int PENDING_WITHDRAW_TICKS = 3;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ItemManager itemManager;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private WorldTypeService worldTypeService;
    @Inject
    private StartingItemsDataProvider startingItemsDataProvider;
    @Inject
    private MembersDataProvider membersDataProvider;
    @Inject
    private BUChatService buChatService;

    private final Observable<Status> status = Observable.of(Status.OFF);
    private final Observable<Checklist> checklist = Observable.of(Checklist.EMPTY);

    private Subscription providerStateSubscription;
    private Subscription accountConfigSubscription;

    // Only used while the bank is open.
    private Map<Integer, Integer> bankQuantities = Collections.emptyMap();
    private boolean bankSeenSinceOpen;
    // Withdraws sent to the server but not yet in the bank container, so fast clicks in one
    // tick cannot each use the same room.
    private final Map<Integer, Integer> pendingWithdraws = new HashMap<>();
    private int lastWithdrawTick;
    private boolean geOffersKnown;
    private static final int NO_ITEM = -1;
    // The item confirmed in the incinerator; lowered once its bank quantity drops.
    private int destroyedItemId = NO_ITEM;
    private int lastReminderTick = -1;

    @Override
    public void startUp() throws Exception {
        providerStateSubscription = startingItemsDataProvider.getState()
            .subscribe((state, old) -> recomputeStatus());
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe((config, old) -> clientThread.invokeLater(this::recomputeStatus));
        // When started while logged in, the GE offers arrived before we were listening.
        geOffersKnown = client.getGameState() == GameState.LOGGED_IN;
    }

    @Override
    public void shutDown() throws Exception {
        if (providerStateSubscription != null) {
            providerStateSubscription.dispose();
            providerStateSubscription = null;
        }
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
        bankQuantities = Collections.emptyMap();
        resetBankState();
        status.set(Status.OFF);
    }

    public Observable<Status> getStatus() {
        return status;
    }

    public Observable<Checklist> getChecklist() {
        return checklist;
    }

    // These read the live state, not the status observable, so they are correct before the first
    // status update. Fail closed: a wrong unlock goes to the whole group.

    public boolean isLockingItems() {
        return computeStatus() != Status.OFF;
    }

    public boolean canUnlock() {
        Status current = computeStatus();
        return current == Status.OFF || current == Status.COUNTED;
    }

    public boolean isCounted() {
        return computeStatus() == Status.COUNTED;
    }

    public boolean isLoading() {
        return computeStatus() == Status.LOADING;
    }

    private void recomputeStatus() {
        status.set(computeStatus());
    }

    private Status computeStatus() {
        AccountConfiguration accountConfiguration = accountConfigurationService.isReady()
            ? accountConfigurationService.getCurrentAccountConfiguration()
            : null;
        if (accountConfiguration == null || !accountConfiguration.isExistingAccount()) {
            return Status.OFF;
        }
        if (startingItemsDataProvider.getState().get() != AbstractDataProvider.State.Ready) {
            return Status.LOADING;
        }
        return startingItemsDataProvider.getMyStartingItems() == null ? Status.NOT_COUNTED : Status.COUNTED;
    }

    public int startingQuantity(int itemId) {
        return startingItemsDataProvider.getMyLedger().starting(itemManager.canonicalize(itemId));
    }

    /** How many of this item may be taken out of the bank. Only valid while the bank is open. */
    public int withdrawRoom(int itemId) {
        int canonicalItemId = itemManager.canonicalize(itemId);
        int room = startingItemsDataProvider.getMyLedger()
            .withdrawRoom(canonicalItemId, bankQuantities.getOrDefault(canonicalItemId, 0));
        return Math.max(0, room - pendingWithdraws.getOrDefault(canonicalItemId, 0));
    }

    /** Called for a withdraw that was let through, before the server has done it. */
    public void addPendingWithdraw(int itemId, int quantity) {
        if (quantity > 0) {
            pendingWithdraws.merge(itemManager.canonicalize(itemId), quantity, Integer::sum);
            lastWithdrawTick = client.getTickCount();
        }
    }

    public void onGameStateChanged(GameStateChanged event) {
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGIN_SCREEN) {
            // Not on world hops, so the reminder does not repeat on every hop.
            lastReminderTick = -1;
        }
        if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING) {
            geOffersKnown = false;
            resetBankState();
        }
    }

    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        // At LOGGED_IN all slots are still empty; the real offers follow in the same tick.
        if (client.getGameState() == GameState.LOGGED_IN) {
            geOffersKnown = true;
        }
    }

    public void onItemContainerChanged(ItemContainerChanged event) {
        // Unsupported worlds, such as seasonal ones, have a different bank.
        if (event.getContainerId() != InventoryID.BANK || !isLockingItems()
            || !worldTypeService.isCurrentWorldSupported()) {
            return;
        }
        Map<Integer, Integer> newQuantities = countByCanonicalId(event.getItemContainer());
        settlePendingWithdraws(newQuantities);
        bankQuantities = newQuantities;
        bankSeenSinceOpen = true;
        if (destroyedItemId != NO_ITEM) {
            lowerDestroyedItem();
        }
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        Widget confirm = client.getWidget(InterfaceID.Bankmain.INCINERATOR_CONFIRM);
        if (confirm == null || confirm.isHidden() || !isLockingItems()) {
            return;
        }
        // The confirmation option is "Destroy ALL".
        if (!Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT).contains("destroy")) {
            return;
        }
        int itemId = findItemId(confirm);
        if (itemId > 0) {
            destroyedItemId = itemManager.canonicalize(itemId);
        }
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.BANKMAIN) {
            resetBankState();
        }
    }

    private void resetBankState() {
        bankSeenSinceOpen = false;
        destroyedItemId = NO_ITEM;
        pendingWithdraws.clear();
    }

    /** A drop in the bank quantity is a pending withdraw that the server has done. */
    private void settlePendingWithdraws(Map<Integer, Integer> newQuantities) {
        pendingWithdraws.replaceAll((itemId, pending) -> pending
            - Math.max(0, bankQuantities.getOrDefault(itemId, 0) - newQuantities.getOrDefault(itemId, 0)));
        pendingWithdraws.values().removeIf(pending -> pending <= 0);
    }

    /** The item shown in the incinerator confirmation. */
    private static int findItemId(Widget widget) {
        if (widget == null) {
            return NO_ITEM;
        }
        if (widget.getItemId() > 0) {
            return widget.getItemId();
        }
        for (Widget[] children : new Widget[][]{
            widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()}) {
            for (Widget child : children == null ? new Widget[0] : children) {
                int itemId = findItemId(child);
                if (itemId > 0) {
                    return itemId;
                }
            }
        }
        return NO_ITEM;
    }

    public void onGameTick() {
        recomputeStatus();
        if (!pendingWithdraws.isEmpty() && client.getTickCount() - lastWithdrawTick > PENDING_WITHDRAW_TICKS) {
            pendingWithdraws.clear();
        }
        if (status.get() != Status.NOT_COUNTED || !worldTypeService.isCurrentWorldSupported()) {
            return;
        }
        checklist.set(computeChecklist());
        sendReminderIfDue();
    }

    private Checklist computeChecklist() {
        Boolean noGeOffers = null;
        if (geOffersKnown) {
            noGeOffers = true;
            for (GrandExchangeOffer offer : client.getGrandExchangeOffers()) {
                if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY) {
                    noGeOffers = false;
                    break;
                }
            }
        }
        Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
        boolean isBankOpen = bankSeenSinceOpen && bankItems != null && !bankItems.isHidden();
        return new Checklist(
            isBankOpen,
            countByCanonicalId(client.getItemContainer(InventoryID.INV)).isEmpty(),
            countByCanonicalId(client.getItemContainer(InventoryID.WORN)).isEmpty(),
            noGeOffers,
            bankQuantities.size(),
            bankQuantities.getOrDefault(ItemID.COINS, 0)
        );
    }

    public CompletableFuture<Void> confirmCount() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            if (computeStatus() != Status.NOT_COUNTED) {
                future.completeExceptionally(new IllegalStateException("Your items are already counted."));
                return;
            }
            if (!computeChecklist().isComplete()) {
                future.completeExceptionally(new IllegalStateException("The checklist is not complete."));
                return;
            }
            // The inventory and equipment are empty, so the bank holds everything.
            StartingItems startingItems = StartingItems.of(new ISOOffsetDateTime(OffsetDateTime.now()), bankQuantities);
            long accountHash = client.getAccountHash();

            startingItemsDataProvider.saveMyStartingItems(startingItems).whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to save starting items", throwable);
                    future.completeExceptionally(throwable);
                    return;
                }
                // Setup reads the member record after a reinstall; failing it does not fail the count.
                updateMyMember(accountHash, StartMode.EXISTING_ACCOUNT).whenComplete((v, memberThrowable) -> {
                    if (memberThrowable != null) {
                        log.warn("Failed to update member record after counting", memberThrowable);
                    }
                });
                clientThread.invokeLater(this::recomputeStatus);
                buChatService.sendHighlightedMessage(
                    "Bronzeman is active! ",
                    NumberFormat.getIntegerInstance().format(startingItems.getItems().size())
                        + " items you already owned are locked in your bank. Everything you get from now on counts."
                );
                future.complete(null);
            });
        });
        return future;
    }

    /** Called when the player leaves Bronzeman, so a new setup starts with a free choice. */
    public CompletableFuture<Void> endItemLock() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            if (!isLockingItems()) {
                future.complete(null);
                return;
            }
            if (accountConfigurationService.getCurrentAccountConfiguration().getStorageMode()
                == AccountConfiguration.StorageMode.LOCAL) {
                // Local progress stays on disk for "Continue Existing", so its locked items stay too.
                future.complete(null);
                return;
            }
            long accountHash = client.getAccountHash();
            // Also delete when nothing is loaded, so an old record cannot come back later.
            startingItemsDataProvider.deleteMyStartingItems()
                .thenCompose(__ -> updateMyMember(accountHash, null))
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to end item locking", throwable);
                        future.completeExceptionally(throwable);
                        return;
                    }
                    future.complete(null);
                });
        });
        return future;
    }

    private CompletableFuture<Void> updateMyMember(long accountHash, StartMode startMode) {
        Map<Long, Member> members = membersDataProvider.getMembersMap();
        Member member = members == null ? null : members.get(accountHash);
        if (member == null) {
            return CompletableFuture.completedFuture(null);
        }
        return membersDataProvider.updateMember(member.withStartMode(startMode));
    }

    /** After an incinerator destroy, the locked quantity is at most what is left in the bank. */
    private void lowerDestroyedItem() {
        StartingItems current = startingItemsDataProvider.getMyStartingItems();
        int bankQuantity = bankQuantities.getOrDefault(destroyedItemId, 0);
        StartingItemsLedger lowered = startingItemsDataProvider.getMyLedger().lower(destroyedItemId, bankQuantity);
        if (current == null || lowered == null) {
            // Not destroyed yet: this bank update came first.
            return;
        }
        destroyedItemId = NO_ITEM;
        startingItemsDataProvider.saveMyStartingItems(StartingItems.of(current.getCountedAt(), lowered.getStartingQuantities()))
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to save lowered starting items", throwable);
                }
            });
    }

    private void sendReminderIfDue() {
        int tick = client.getTickCount();
        if (lastReminderTick != -1 && tick - lastReminderTick < REMINDER_INTERVAL_TICKS) {
            return;
        }
        lastReminderTick = tick;
        buChatService.sendHighlightedMessage(
            "Almost there! ",
            "Unlocks are paused until you count your items in the Bronzeman Unleashed panel."
        );
    }

    private Map<Integer, Integer> countByCanonicalId(ItemContainer container) {
        Map<Integer, Integer> quantities = new HashMap<>();
        if (container == null) {
            return quantities;
        }
        for (Item item : container.getItems()) {
            if (item != null && item.getId() > 0 && item.getQuantity() > 0) {
                quantities.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
            }
        }
        return quantities;
    }

    public enum Status {
        OFF,
        LOADING,
        NOT_COUNTED,
        COUNTED
    }

    @Value
    public static class Checklist {

        static final Checklist EMPTY = new Checklist(false, false, false, null, 0, 0);

        boolean bankOpen;
        boolean inventoryEmpty;
        boolean equipmentEmpty;
        // Null until the GE offers are known after login.
        Boolean noGeOffers;
        int bankItems;
        int coins;

        public boolean isComplete() {
            return bankOpen && inventoryEmpty && equipmentEmpty && Boolean.TRUE.equals(noGeOffers);
        }
    }
}
