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
import com.elertan.remote.local.LocalStorageAdapters.UnreadableLocalProgressException;
import com.elertan.remote.local.LocalStorageSession;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
    @Getter
    private final Observable<LocalProgressOpenFailure> localProgressOpenFailure = Observable.empty();
    private final AccountConfigurationService accountConfigurationService;
    private final Client client;
    private final FirebaseStorageSession.Factory firebaseStorageSessionFactory;
    private final LocalStorageSession.Factory localStorageSessionFactory;
    private Subscription accountConfigSubscription;
    private final AtomicInteger sessionGeneration = new AtomicInteger();
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
        // Opening a storage session can now finish asynchronously. Track which open attempt is the
        // latest so an older callback cannot overwrite a newer account configuration.
        int generation = sessionGeneration.incrementAndGet();
        try {
            clearCurrentSession();
        } catch (Exception e) {
            log.error("Failed to clear current storage session", e);
        }

        if (accountConfiguration == null) {
            return;
        }

        openStorageSession(accountConfiguration).whenComplete((newStorageSession, throwable) -> {
            if (generation != sessionGeneration.get()) {
                closeSessionQuietly(newStorageSession);
                return;
            }

            if (throwable != null) {
                handleStorageOpenFailure(accountConfiguration, throwable);
                return;
            }

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
        });
    }

    private CompletableFuture<StorageSession> openStorageSession(AccountConfiguration accountConfiguration) {
        StorageMode storageMode = accountConfiguration.getStorageMode();
        if (storageMode == null) {
            storageMode = StorageMode.FIREBASE;
        }

        if (storageMode == StorageMode.LOCAL) {
            Long localAccountHash = accountConfiguration.getLocalAccountHash();
            if (localAccountHash == null) {
                log.error("Local storage mode is missing localAccountHash");
                return CompletableFuture.completedFuture(null);
            }
            return openLocalStorageSession(localAccountHash);
        }

        FirebaseRealtimeDatabaseURL url = accountConfiguration.getFirebaseRealtimeDatabaseURL();
        if (url == null) {
            log.error("Firebase storage mode is missing Firebase URL");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.completedFuture(firebaseStorageSessionFactory.create(url));
    }

    private CompletableFuture<StorageSession> openLocalStorageSession(long localAccountHash) {
        LocalStorageSession localStorageSession = localStorageSessionFactory.create(localAccountHash);
        CompletableFuture<GameRules> readGameRulesFuture =
            localStorageSession.getGameRulesStoragePort().read();
        CompletableFuture<Map<Integer, UnlockedItem>> readUnlockedItemsFuture =
            localStorageSession.getUnlockedItemsStoragePort().readAll();

        return CompletableFuture.allOf(readGameRulesFuture, readUnlockedItemsFuture)
            .handle((__, throwable) -> {
                if (throwable != null) {
                    closeSessionQuietly(localStorageSession);
                    throw wrapAsCompletionException(unwrap(throwable));
                }

                return (StorageSession) localStorageSession;
            });
    }

    private void handleStorageOpenFailure(
        AccountConfiguration accountConfiguration,
        Throwable throwable
    ) {
        Throwable cause = unwrap(throwable);
        if (accountConfiguration.getStorageMode() == StorageMode.LOCAL
            && cause instanceof UnreadableLocalProgressException) {
            log.error("Could not open local progress", cause);
            localProgressOpenFailure.set(new LocalProgressOpenFailure((UnreadableLocalProgressException) cause));
            return;
        }

        log.error("Failed to open storage session", cause);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        // CompletableFuture commonly wraps the real failure in one or more CompletionExceptions.
        // Peel those away so callers can reason about the underlying exception type.
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static CompletionException wrapAsCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException) {
            return (CompletionException) throwable;
        }
        return new CompletionException(throwable);
    }

    private static void closeSessionQuietly(StorageSession storageSession) {
        if (storageSession == null) {
            return;
        }

        try {
            storageSession.close();
        } catch (Exception e) {
            log.error("Failed to close storage session", e);
        }
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

    public static final class LocalProgressOpenFailure {

        @Getter
        private final UnreadableLocalProgressException cause;

        public LocalProgressOpenFailure(UnreadableLocalProgressException cause) {
            this.cause = cause;
        }
    }
}
