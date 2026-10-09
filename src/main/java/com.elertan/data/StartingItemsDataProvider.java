package com.elertan.data;

import com.elertan.AccountConfigurationService;
import com.elertan.models.AccountConfiguration;
import com.elertan.itemlock.StartingItemsLedger;
import com.elertan.models.StartingItems;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.StorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Holds the starting (locked) items of the local player. For new accounts it reads nothing.
 */
@Slf4j
@Singleton
public class StartingItemsDataProvider extends AbstractDataProvider {

    @Inject
    private StorageService storageService;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private Client client;

    private KeyValueStoragePort<Long, StartingItems> keyValueStoragePort;
    private volatile StartingItems myStartingItems;
    private volatile StartingItemsLedger myLedger = StartingItemsLedger.empty();

    @Override
    protected StorageService getStorageService() {
        return storageService;
    }

    @Override
    protected void onRemoteStorageReady() {
        keyValueStoragePort = storageService.getStartingItemsStoragePort();
        long accountHash = client.getAccountHash();

        AccountConfiguration accountConfiguration =
            accountConfigurationService.getAccountConfiguration(accountHash);
        if (accountConfiguration == null || !accountConfiguration.isExistingAccount()) {
            setMyStartingItems(null);
            setState(State.Ready);
            return;
        }

        keyValueStoragePort.read(accountHash).whenComplete((startingItems, throwable) -> {
            if (throwable != null) {
                // Stay NotReady: an existing account gets no unlocks without its starting items.
                log.error("StartingItemsDataProvider read failed", throwable);
                return;
            }
            setMyStartingItems(startingItems);
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        keyValueStoragePort = null;
        setMyStartingItems(null);
    }

    /** Null when not counted, or for a new account. */
    public StartingItems getMyStartingItems() {
        return myStartingItems;
    }

    /** Empty when not counted, or for a new account. */
    public StartingItemsLedger getMyLedger() {
        return myLedger;
    }

    private void setMyStartingItems(StartingItems startingItems) {
        myLedger = startingItems == null ? StartingItemsLedger.empty() : new StartingItemsLedger(startingItems.toQuantityMap());
        myStartingItems = startingItems;
    }

    public CompletableFuture<Void> saveMyStartingItems(StartingItems startingItems) {
        KeyValueStoragePort<Long, StartingItems> port = keyValueStoragePort;
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage is not ready"));
        }
        return port.update(client.getAccountHash(), startingItems)
            .thenRun(() -> setMyStartingItems(startingItems));
    }

    public CompletableFuture<Void> deleteMyStartingItems() {
        KeyValueStoragePort<Long, StartingItems> port = keyValueStoragePort;
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage is not ready"));
        }
        return port.delete(client.getAccountHash())
            .thenRun(() -> setMyStartingItems(null));
    }
}
