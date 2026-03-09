package com.elertan.remote;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.event.BUEvent;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.AccountConfiguration.StorageMode;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.firebase.FirebaseStorageSession;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.local.LocalStorageSession;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;

@Slf4j
@Singleton
public class StorageService implements BUPluginLifecycle {

    @Getter
    private final Observable<State> state = Observable.of(State.NotReady);
    private final AccountConfigurationService accountConfigurationService;
    private final Client client;
    private final FirebaseStorageSession.Factory firebaseStorageSessionFactory;
    private final LocalStorageSession.Factory localStorageSessionFactory;
    private Subscription accountConfigSubscription;
    private StorageSession storageSession;
    @Getter
    private KeyValueStoragePort<Long, Member> membersStoragePort;
    @Getter
    private KeyValueStoragePort<Integer, UnlockedItem> unlockedItemsStoragePort;
    @Getter
    private ObjectStoragePort<GameRules> gameRulesStoragePort;
    @Getter
    private ObjectListStoragePort<BUEvent> lastEventStoragePort;
    @Getter
    private KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByStoragePort;

    @Inject
    public StorageService(
        AccountConfigurationService accountConfigurationService,
        Client client,
        FirebaseStorageSession.Factory firebaseStorageSessionFactory,
        LocalStorageSession.Factory localStorageSessionFactory
    ) {
        this.accountConfigurationService = accountConfigurationService;
        this.client = client;
        this.firebaseStorageSessionFactory = firebaseStorageSessionFactory;
        this.localStorageSessionFactory = localStorageSessionFactory;
    }

    @Override
    public void startUp() {
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::useAccountConfiguration);
        if (accountConfigurationService.isReady() && client.getGameState() == GameState.LOGGED_IN) {
            useAccountConfiguration(accountConfigurationService.getCurrentAccountConfiguration());
        }
    }

    @Override
    public void shutDown() throws Exception {
        clearCurrentSession();
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
    }

    public CompletableFuture<State> await(Duration timeout) {
        return waitForValue(state, State.Ready, timeout);
    }

    private static <T> CompletableFuture<T> waitForValue(
        Observable<T> observable,
        T targetValue,
        Duration timeout
    ) {
        CompletableFuture<T> future = new CompletableFuture<>();

        if (observable.get() == targetValue) {
            future.complete(targetValue);
            return future;
        }

        // The subscription must be visible inside the callback so we can dispose it after the
        // target value is observed. Java lambdas require captured variables to be effectively final,
        // so we keep it in a one-element array.
        Subscription[] subscriptionHolder = new Subscription[1];
        subscriptionHolder[0] = observable.subscribe((newValue, oldValue) -> {
            if (newValue == targetValue && !future.isDone()) {
                subscriptionHolder[0].dispose();
                future.complete(newValue);
            }
        });

        if (observable.get() == targetValue && !future.isDone()) {
            subscriptionHolder[0].dispose();
            future.complete(targetValue);
        }

        if (timeout != null) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                if (!future.isDone()) {
                    subscriptionHolder[0].dispose();
                    future.completeExceptionally(new TimeoutException("Timeout waiting for value"));
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            future.whenComplete((result, ex) -> scheduler.shutdown());
        }

        return future;
    }

    private void useAccountConfiguration(AccountConfiguration accountConfiguration) {
        try {
            clearCurrentSession();
        } catch (Exception e) {
            log.error("Failed to clear current storage session", e);
        }

        if (accountConfiguration == null) {
            return;
        }

        StorageSession newStorageSession = createStorageSession(accountConfiguration);
        if (newStorageSession == null) {
            return;
        }

        storageSession = newStorageSession;
        membersStoragePort = newStorageSession.getMembersStoragePort();
        unlockedItemsStoragePort = newStorageSession.getUnlockedItemsStoragePort();
        gameRulesStoragePort = newStorageSession.getGameRulesStoragePort();
        lastEventStoragePort = newStorageSession.getLastEventStoragePort();
        groundItemOwnedByStoragePort = newStorageSession.getGroundItemOwnedByStoragePort();
        state.set(State.Ready);
    }

    private StorageSession createStorageSession(AccountConfiguration accountConfiguration) {
        StorageMode storageMode = accountConfiguration.getStorageMode();
        if (storageMode == null) {
            storageMode = StorageMode.FIREBASE;
        }

        if (storageMode == StorageMode.LOCAL) {
            Long localAccountHash = accountConfiguration.getLocalAccountHash();
            if (localAccountHash == null) {
                log.error("Local storage mode is missing localAccountHash");
                return null;
            }
            return localStorageSessionFactory.create(localAccountHash);
        }

        FirebaseRealtimeDatabaseURL url = accountConfiguration.getFirebaseRealtimeDatabaseURL();
        if (url == null) {
            log.error("Firebase storage mode is missing Firebase URL");
            return null;
        }

        return firebaseStorageSessionFactory.create(url);
    }

    private void clearCurrentSession() throws Exception {
        state.set(State.NotReady);

        if (storageSession != null) {
            storageSession.close();
            storageSession = null;
        }

        groundItemOwnedByStoragePort = null;
        lastEventStoragePort = null;
        membersStoragePort = null;
        unlockedItemsStoragePort = null;
        gameRulesStoragePort = null;
    }

    public enum State {
        NotReady,
        Ready
    }
}
