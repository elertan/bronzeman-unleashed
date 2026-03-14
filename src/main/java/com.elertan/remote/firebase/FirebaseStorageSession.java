package com.elertan.remote.firebase;

import com.elertan.event.BUEvent;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.KeyListStoragePort;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.ObjectListStoragePort;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.StorageSession;
import com.elertan.remote.firebase.storageAdapters.GameRulesFirebaseObjectStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.GroundItemOwnedByKeyListStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.LastEventFirebaseObjectListStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.MembersFirebaseKeyValueStorageAdapter;
import com.elertan.remote.firebase.storageAdapters.UnlockedItemsFirebaseKeyValueStorageAdapter;
import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;

public class FirebaseStorageSession implements StorageSession {

    private final FirebaseRealtimeDatabase firebaseRealtimeDatabase;
    private final KeyValueStoragePort<Long, Member> membersStoragePort;
    private final KeyValueStoragePort<Integer, UnlockedItem> unlockedItemsStoragePort;
    private final ObjectStoragePort<GameRules> gameRulesStoragePort;
    private final ObjectListStoragePort<BUEvent> lastEventStoragePort;
    private final KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByStoragePort;

    public FirebaseStorageSession(
        OkHttpClient httpClient,
        Gson gson,
        FirebaseRealtimeDatabaseURL url
    ) {
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

        firebaseRealtimeDatabase.getStream().start();
    }

    @Override
    public KeyValueStoragePort<Long, Member> getMembersStoragePort() {
        return membersStoragePort;
    }

    @Override
    public KeyValueStoragePort<Integer, UnlockedItem> getUnlockedItemsStoragePort() {
        return unlockedItemsStoragePort;
    }

    @Override
    public ObjectStoragePort<GameRules> getGameRulesStoragePort() {
        return gameRulesStoragePort;
    }

    @Override
    public ObjectListStoragePort<BUEvent> getLastEventStoragePort() {
        return lastEventStoragePort;
    }

    @Override
    public KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> getGroundItemOwnedByStoragePort() {
        return groundItemOwnedByStoragePort;
    }

    @Override
    public void close() throws Exception {
        groundItemOwnedByStoragePort.close();
        lastEventStoragePort.close();
        membersStoragePort.close();
        unlockedItemsStoragePort.close();
        gameRulesStoragePort.close();

        firebaseRealtimeDatabase.getStream().stop();
        firebaseRealtimeDatabase.close();
    }

    @Singleton
    public static final class Factory {

        private final OkHttpClient httpClient;
        private final Gson gson;

        @Inject
        public Factory(OkHttpClient httpClient, Gson gson) {
            this.httpClient = httpClient;
            this.gson = gson;
        }

        public FirebaseStorageSession create(FirebaseRealtimeDatabaseURL url) {
            return new FirebaseStorageSession(httpClient, gson, url);
        }
    }
}
