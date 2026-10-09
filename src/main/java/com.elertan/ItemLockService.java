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
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Item locking for existing accounts.
 *
 * <p>The model: counting needs an empty inventory and equipment, and locked items can never be
 * withdrawn. So locked items only exist in the bank, and everything the player carries was earned.
 * The bank is therefore not an unlock source, and a withdraw may only take what is above the
 * locked quantity. Every public check returns early for new accounts, so they see no change.
 *
 * <p>Known gaps, also shown to the player during setup: storage that is not counted, item charges,
 * rune pouches and containers filled from the bank, and the POH servant fetching from the bank.
 */
@Slf4j
@Singleton
public class ItemLockService implements BUPluginLifecycle {

    private static final int REMINDER_INTERVAL_TICKS = 1000; // about 10 minutes

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

    private volatile StartingItems ledgerSource;
    private volatile StartingItemsLedger ledger = StartingItemsLedger.empty();
    // Canonical item ID -> quantity, from the bank container. Only used while the bank is open.
    private Map<Integer, Long> bankQuantities = Collections.emptyMap();
    private boolean bankSeenSinceOpen;
    private boolean geOffersKnown;
    // Set by an incinerator "Destroy"; the next bank update lowers the locked quantities.
    private boolean lowerOnNextBankUpdate;
    private int lastReminderTick = -1;

    @Override
    public void startUp() throws Exception {
        providerStateSubscription = startingItemsDataProvider.getState()
            .subscribe((state, old) -> recomputeStatus());
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe((config, old) -> clientThread.invokeLater(this::recomputeStatus));
        // Started while logged in (for example when the plugin is turned on): the GE offers
        // arrived before we were listening, but the client already has them.
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
        bankSeenSinceOpen = false;
        lowerOnNextBankUpdate = false;
        ledgerSource = null;
        ledger = StartingItemsLedger.empty();
        status.set(Status.OFF);
    }

    // ---- State ----

    public Observable<Status> getStatus() {
        return status;
    }

    public Observable<Checklist> getChecklist() {
        return checklist;
    }

    // The checks below read the live state instead of the status observable, so they are
    // correct before the first status update. Fail closed: a wrong unlock goes to the whole group.

    public boolean isLockingItems() {
        return computeStatus() != Status.OFF;
    }

    /** False while an existing account is not counted or its data is loading. */
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
        AccountConfiguration accountConfiguration = null;
        if (accountConfigurationService.isReady()) {
            accountConfiguration = accountConfigurationService.getCurrentAccountConfiguration();
        }
        if (accountConfiguration == null || !accountConfiguration.isExistingAccount()) {
            return Status.OFF;
        }
        if (startingItemsDataProvider.getState().get() != AbstractDataProvider.State.Ready) {
            return Status.LOADING;
        }
        return startingItemsDataProvider.getMyStartingItems() == null ? Status.NOT_COUNTED : Status.COUNTED;
    }

    /** The ledger for the saved starting items. Rebuilt only when the saved items change. */
    private StartingItemsLedger currentLedger() {
        StartingItems startingItems = startingItemsDataProvider.getMyStartingItems();
        if (startingItems != ledgerSource) {
            ledgerSource = startingItems;
            ledger = startingItems == null
                ? StartingItemsLedger.empty()
                : new StartingItemsLedger(startingItems.toQuantityMap());
        }
        return ledger;
    }

    // ---- Quantities (client thread, bank open) ----

    public int startingQuantity(int itemId) {
        return currentLedger().starting(canonicalize(itemId));
    }

    /** How many of this item the player may take out of the bank. */
    public long withdrawRoom(int itemId) {
        int canonicalItemId = canonicalize(itemId);
        return currentLedger().withdrawRoom(canonicalItemId, bankQuantities.getOrDefault(canonicalItemId, 0L));
    }

    private int canonicalize(int itemId) {
        return itemManager.canonicalize(itemId);
    }

    private boolean isBankLive() {
        Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return bankSeenSinceOpen && bankItems != null && !bankItems.isHidden();
    }

    // ---- Events ----

    public void onGameStateChanged(GameStateChanged event) {
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGIN_SCREEN) {
            // Not on world hops, so the reminder does not repeat on every hop.
            lastReminderTick = -1;
        }
        if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING) {
            geOffersKnown = false;
            bankSeenSinceOpen = false;
            lowerOnNextBankUpdate = false;
        }
    }

    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        // At the LOGGED_IN event all slots are still EMPTY; the real offers follow in the same tick.
        if (client.getGameState() == GameState.LOGGED_IN) {
            geOffersKnown = true;
        }
    }

    public void onItemContainerChanged(ItemContainerChanged event) {
        // Seasonal and other unsupported worlds have a different bank.
        if (event.getContainerId() != InventoryID.BANK || !isLockingItems()
            || !worldTypeService.isCurrentWorldSupported()) {
            return;
        }
        bankQuantities = countByCanonicalId(event.getItemContainer());
        bankSeenSinceOpen = true;
        if (lowerOnNextBankUpdate) {
            lowerOnNextBankUpdate = false;
            lowerToBank();
        }
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        Widget widget = event.getWidget();
        if (widget != null && WidgetUtil.componentToInterface(widget.getId()) == InterfaceID.BANKMAIN
            && "Destroy".equalsIgnoreCase(event.getMenuOption())) {
            lowerOnNextBankUpdate = true;
        }
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.BANKMAIN) {
            bankSeenSinceOpen = false;
            lowerOnNextBankUpdate = false;
        }
    }

    public void onGameTick() {
        recomputeStatus();
        if (status.get() != Status.NOT_COUNTED || !worldTypeService.isCurrentWorldSupported()) {
            return;
        }
        checklist.set(computeChecklist());
        sendReminderIfDue();
    }

    // ---- Counting ----

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
        return new Checklist(
            isBankLive() && worldTypeService.isCurrentWorldSupported(),
            countByCanonicalId(client.getItemContainer(InventoryID.INV)).isEmpty(),
            countByCanonicalId(client.getItemContainer(InventoryID.WORN)).isEmpty(),
            noGeOffers
        );
    }

    /** What would be counted now, for the confirmation. */
    public CompletableFuture<CountSummary> previewCount() {
        CompletableFuture<CountSummary> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            if (!computeChecklist().isComplete()) {
                future.completeExceptionally(new IllegalStateException("The checklist is not complete."));
                return;
            }
            future.complete(new CountSummary(bankQuantities.size(), bankQuantities.getOrDefault(ItemID.COINS, 0L)));
        });
        return future;
    }

    /** Counts again and saves the starting items. */
    public CompletableFuture<Integer> confirmCount() {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            if (computeStatus() != Status.NOT_COUNTED) {
                future.completeExceptionally(new IllegalStateException("Your items are already counted."));
                return;
            }
            if (!computeChecklist().isComplete()) {
                future.completeExceptionally(new IllegalStateException("The checklist is not complete."));
                return;
            }
            // The checklist makes sure nothing is carried and the bank is open, so the bank holds everything.
            Map<Integer, Integer> startingQuantities = new HashMap<>();
            bankQuantities.forEach((itemId, quantity) ->
                startingQuantities.put(itemId, (int) Math.min(quantity, Integer.MAX_VALUE)));
            StartingItems startingItems = StartingItems.of(new ISOOffsetDateTime(OffsetDateTime.now()), startingQuantities);
            long accountHash = client.getAccountHash();

            startingItemsDataProvider.saveMyStartingItems(startingItems).whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to save starting items", throwable);
                    future.completeExceptionally(throwable);
                    return;
                }
                // The member record is only a copy (setup reads it after a reinstall), so it does not fail the count.
                updateMyMember(accountHash, StartMode.EXISTING_ACCOUNT).whenComplete((v, memberThrowable) -> {
                    if (memberThrowable != null) {
                        log.warn("Failed to update member record after counting", memberThrowable);
                    }
                });
                clientThread.invokeLater(this::recomputeStatus);
                int count = startingItems.getItems().size();
                buChatService.sendHighlightedMessage(
                    "Bronzeman is active! ",
                    NumberFormat.getIntegerInstance().format(count)
                        + " items you already owned are locked in your bank. Everything you get from now on counts."
                );
                future.complete(count);
            });
        });
        return future;
    }

    // ---- Leaving Bronzeman ----

    /**
     * Ends item locking for this account: deletes its locked items and clears the member copy.
     * Called when the player leaves Bronzeman, so a new setup starts with a free choice.
     */
    public CompletableFuture<Void> endItemLock() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            if (!isLockingItems()) {
                future.complete(null);
                return;
            }
            long accountHash = client.getAccountHash();
            // Always delete, also when nothing is cached (for example while loading), so an old
            // record cannot come back after a new setup.
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

    // ---- Counted ----

    /** After an incinerator destroy, a locked quantity is at most what is left in the bank. */
    private void lowerToBank() {
        StartingItems current = startingItemsDataProvider.getMyStartingItems();
        if (current == null) {
            return;
        }
        StartingItemsLedger lowered = currentLedger().lowerTo(bankQuantities);
        if (lowered == null) {
            return;
        }
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

    /** Counts a container by un-noted item ID, without placeholders. */
    private Map<Integer, Long> countByCanonicalId(ItemContainer container) {
        Map<Integer, Long> quantities = new HashMap<>();
        if (container == null) {
            return quantities;
        }
        for (Item item : container.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }
            quantities.merge(canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
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

        static final Checklist EMPTY = new Checklist(false, false, false, null);

        boolean bankOpen;
        boolean inventoryEmpty;
        boolean equipmentEmpty;
        // Null while the GE offers are not known yet after login.
        Boolean noGeOffers;

        public boolean isComplete() {
            return bankOpen && inventoryEmpty && equipmentEmpty && Boolean.TRUE.equals(noGeOffers);
        }
    }

    @Value
    public static class CountSummary {

        int bankItems;
        long coins;
    }
}
