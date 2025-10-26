package com.elertan.remote;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.event.BUEvent;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.firebase.FirebaseSSEStream;
import com.elertan.remote.firebase.storageAdapters.GameRulesFirebaseObjectStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.LastEventFirebaseObjectStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.MembersFirebaseKeyValueStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.UnlockedItemsFirebaseKeyValueStorageAdapter;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class RemoteStorageService implements BUPluginLifecycle {
    public enum State {
        NotReady,
        Ready
    }

    @Inject
    private OkHttpClient httpClient;
    @Inject
    private Gson gson;
    @Inject
    private AccountConfigurationService accountConfigurationService;

    private final ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();

    @Getter
    private State state = State.NotReady;

    private FirebaseRealtimeDatabase firebaseRealtimeDatabase;

    @Getter
    private KeyValueStoragePort<Long, Member> membersStoragePort;
    @Getter
    private KeyValueStoragePort<Integer, UnlockedItem> unlockedItemsStoragePort;
    @Getter
    private ObjectStoragePort<GameRules> gameRulesStoragePort;
    @Getter
    private ObjectStoragePort<BUEvent> lastEventStoragePort;

    @Override
    public void startUp() {
        accountConfigurationService.addCurrentAccountConfigurationChangeListener(this::currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        clearCurrentDataport();
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(this::currentAccountConfigurationChangeListener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    private void setState(State state) {
        if (this.state == state) return;
        this.state = state;
        for (Consumer<State> listener : stateListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("set state listener error", e);
            }
        }
    }

    private void currentAccountConfigurationChangeListener(AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            try {
                clearCurrentDataport();
            } catch (Exception e) {
                log.error("Failed to clear current data port", e);
            }
            return;
        }

        // We can support different kinds of data ports here later
        FirebaseRealtimeDatabaseURL url = accountConfiguration.getFirebaseRealtimeDatabaseURL();
        configureFromFirebaseRealtimeDatabase(url);

        setState(State.Ready);
    }

    private void clearCurrentDataport() throws Exception {
        setState(State.NotReady);

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
    }

    private void configureFromFirebaseRealtimeDatabase(FirebaseRealtimeDatabaseURL url) {
        firebaseRealtimeDatabase = new FirebaseRealtimeDatabase(httpClient, gson, url);

        lastEventStoragePort = new LastEventFirebaseObjectStorageAdapter(firebaseRealtimeDatabase, gson);
        membersStoragePort = new MembersFirebaseKeyValueStorageAdapter(firebaseRealtimeDatabase, gson);
        unlockedItemsStoragePort = new UnlockedItemsFirebaseKeyValueStorageAdapter(firebaseRealtimeDatabase, gson);
        gameRulesStoragePort = new GameRulesFirebaseObjectStorageAdapter(firebaseRealtimeDatabase, gson);

        FirebaseSSEStream stream = firebaseRealtimeDatabase.getStream();
        stream.start();
    }
}
