package com.elertan.remote;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.event.BUEvent;
import com.elertan.event.BUEventGson;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.firebase.FirebaseKeyListStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseKeyValueStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseObjectListStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseObjectStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import com.google.gson.Gson;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    @Getter
    private final Observable<State> state = Observable.of(State.NotReady);
    private Subscription accountConfigSubscription;
    @Inject private OkHttpClient httpClient;
    @Inject private Client client;
    @Inject private Gson gson;
    @Inject private AccountConfigurationService accountConfigurationService;
    private FirebaseRealtimeDatabase firebaseRealtimeDatabase;
    private final List<AutoCloseable> activePorts = new ArrayList<>();
    @Getter private KeyValueStoragePort<Long, Member> membersStoragePort;
    @Getter private KeyValueStoragePort<Integer, UnlockedItem> unlockedItemsStoragePort;
    @Getter private ObjectStoragePort<GameRules> gameRulesStoragePort;
    @Getter private ObjectListStoragePort<BUEvent> lastEventStoragePort;
    @Getter private KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByStoragePort;

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
        if (accountConfigSubscription != null) { accountConfigSubscription.dispose(); accountConfigSubscription = null; }
    }

    public CompletableFuture<State> await(Duration timeout) {
        return Observable.awaitValue(state, State.Ready, timeout);
    }

    private void useAccountConfiguration(AccountConfiguration config) {
        try { clearCurrentDataport(); } catch (Exception e) { log.error("Failed to clear current data port", e); }
        if (config == null) return;
        configureFromFirebaseRealtimeDatabase(config.getFirebaseRealtimeDatabaseURL());
        state.set(State.Ready);
    }

    private void clearCurrentDataport() throws Exception {
        state.set(State.NotReady);
        for (AutoCloseable port : activePorts) {
            try { port.close(); } catch (Exception e) { log.error("Error closing port", e); }
        }
        activePorts.clear();
        membersStoragePort = null;
        unlockedItemsStoragePort = null;
        gameRulesStoragePort = null;
        lastEventStoragePort = null;
        groundItemOwnedByStoragePort = null;
        if (firebaseRealtimeDatabase != null) { firebaseRealtimeDatabase.getStream().stop(); firebaseRealtimeDatabase = null; }
    }

    private void configureFromFirebaseRealtimeDatabase(FirebaseRealtimeDatabaseURL url) {
        FirebaseRealtimeDatabase db = new FirebaseRealtimeDatabase(httpClient, gson, url);
        firebaseRealtimeDatabase = db;

        gameRulesStoragePort = register(new FirebaseObjectStorageAdapterBase<>(
            "/GameRules", db, gson::toJsonTree, el -> gson.fromJson(el, GameRules.class)));
        lastEventStoragePort = register(new FirebaseObjectListStorageAdapterBase<>(
            "/LastEvent", db, ev -> BUEventGson.serialize(gson, ev), el -> BUEventGson.deserialize(gson, el)));
        membersStoragePort = register(new FirebaseKeyValueStorageAdapterBase<>(
            "/Members", db, gson, Long::parseLong, Object::toString,
            el -> (el == null || el.isJsonNull()) ? null : gson.fromJson(el, Member.class)));
        unlockedItemsStoragePort = register(new FirebaseKeyValueStorageAdapterBase<>(
            "/UnlockedItems", db, gson, Integer::parseInt, Object::toString,
            el -> (el == null || el.isJsonNull()) ? null : gson.fromJson(el, UnlockedItem.class)));
        groundItemOwnedByStoragePort = register(new FirebaseKeyListStorageAdapterBase<>(
            "/GroundItemOwnedBy", db, gson, GroundItemOwnedByKey::fromKey, GroundItemOwnedByKey::toKey,
            el -> (el == null || el.isJsonNull()) ? null : gson.fromJson(el, GroundItemOwnedByData.class)));

        db.getStream().start();
    }

    @SuppressWarnings("unchecked")
    private <T extends AutoCloseable> T register(T port) { activePorts.add(port); return port; }

    public enum State { NotReady, Ready }
}
