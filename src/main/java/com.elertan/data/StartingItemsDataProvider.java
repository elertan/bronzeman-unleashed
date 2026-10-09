package com.elertan.data;

import com.elertan.AccountConfigurationService;
import com.elertan.models.AccountConfiguration;
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
            myStartingItems = null;
            setState(State.Ready);
            return;
        }

        keyValueStoragePort.read(accountHash).whenComplete((startingItems, throwable) -> {
            if (throwable != null) {
                // Stay NotReady: an existing account gets no unlocks without its starting items.
                log.error("StartingItemsDataProvider read failed", throwable);
                return;
            }
            myStartingItems = startingItems;
            log.debug("StartingItemsDataProvider initialized, counted: {}", startingItems != null);
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        keyValueStoragePort = null;
        myStartingItems = null;
    }

    /** The local player's starting items, or null when not counted (or not an existing account). */
    public StartingItems getMyStartingItems() {
        return myStartingItems;
    }

    public CompletableFuture<Void> saveMyStartingItems(StartingItems startingItems) {
        KeyValueStoragePort<Long, StartingItems> port = keyValueStoragePort;
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage is not ready"));
        }
        return port.update(client.getAccountHash(), startingItems)
            .thenRun(() -> myStartingItems = startingItems);
    }

    public CompletableFuture<Void> deleteMyStartingItems() {
        KeyValueStoragePort<Long, StartingItems> port = keyValueStoragePort;
        if (port == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage is not ready"));
        }
        return port.delete(client.getAccountHash())
            .thenRun(() -> myStartingItems = null);
    }
}
