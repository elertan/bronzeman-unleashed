package com.elertan.remote;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.event.BUEvent;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.firebase.FirebaseSSEStream;
import com.elertan.remote.firebase.storageAdapters.GameRulesFirebaseObjectStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.GroundItemOwnedByKeyListStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.LastEventFirebaseObjectListStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.MembersFirebaseKeyValueStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.UnlockedItemsFirebaseKeyValueStorageAdapter;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import com.google.gson.Gson;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import okhttp3.OkHttpClient;

@Slf4j
@Singleton
public class RemoteStorageService implements BUPluginLifecycle {

    private final Observable<State> state = new Observable<>("RemoteStorageService.state");
    private Subscription accountConfigSubscription;
    @Inject
    private OkHttpClient httpClient;
    @Inject
    private Client client;
    @Inject
    private Gson gson;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    private FirebaseRealtimeDatabase firebaseRealtimeDatabase;
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
        clearCurrentDataport();
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
    }

    /**
     * Observable for state changes.
     */
    public Observable<State> state() {
        return state;
    }

    /**
     * Get current state.
     */
    public State getState() {
        return state.get();
    }

    /**
     * Wait until remote storage is ready.
     */
    public CompletableFuture<State> waitUntilReady(Duration timeout) {
        return state.waitUntilReady(timeout);
    }

    private void useAccountConfiguration(AccountConfiguration accountConfiguration) {
        try {
            clearCurrentDataport();
        } catch (Exception e) {
            log.error("Failed to clear current data port", e);
        }
        if (accountConfiguration == null) {
            return;
        }

        // We can support different kinds of data ports here later
        FirebaseRealtimeDatabaseURL url = accountConfiguration.getFirebaseRealtimeDatabaseURL();
        configureFromFirebaseRealtimeDatabase(url);

        state.set(State.Ready);
    }

    private void clearCurrentDataport() throws Exception {
        state.set(State.NotReady);

        if (groundItemOwnedByStoragePort != null) {
            groundItemOwnedByStoragePort.close();
            groundItemOwnedByStoragePort = null;
        }
        if (lastEventStoragePort != null) {
            lastEventStoragePort.close();
            lastEventStoragePort = null;
        }
        if (membersStoragePort != null) {
            membersStoragePort.close();
            membersStoragePort = null;
        }
        if (unlockedItemsStoragePort != null) {
            unlockedItemsStoragePort.close();
            unlockedItemsStoragePort = null;
        }
        if (gameRulesStoragePort != null) {
            gameRulesStoragePort.close();
            gameRulesStoragePort = null;
        }

        if (firebaseRealtimeDatabase != null) {
            FirebaseSSEStream stream = firebaseRealtimeDatabase.getStream();
            stream.stop();

            firebaseRealtimeDatabase = null;
        }

        log.info("Dataport has been cleared");
    }

    private void configureFromFirebaseRealtimeDatabase(FirebaseRealtimeDatabaseURL url) {
        firebaseRealtimeDatabase = new FirebaseRealtimeDatabase(httpClient, gson, url);

        groundItemOwnedByStoragePort = new GroundItemOwnedByKeyListStorageAdapter(
            firebaseRealtimeDatabase,
            gson
        );
        lastEventStoragePort = new LastEventFirebaseObjectListStorageAdapter(
            firebaseRealtimeDatabase,
            gson
        );
        membersStoragePort = new MembersFirebaseKeyValueStorageAdapter(
            firebaseRealtimeDatabase,
            gson
        );
        unlockedItemsStoragePort = new UnlockedItemsFirebaseKeyValueStorageAdapter(
            firebaseRealtimeDatabase,
            gson
        );
        gameRulesStoragePort = new GameRulesFirebaseObjectStorageAdapter(
            firebaseRealtimeDatabase,
            gson
        );

        FirebaseSSEStream stream = firebaseRealtimeDatabase.getStream();
        stream.start();
    }

    public enum State {
        NotReady,
        Ready
    }
}
