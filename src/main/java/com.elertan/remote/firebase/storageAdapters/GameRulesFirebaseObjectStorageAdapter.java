package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.GameRules;
import com.elertan.remote.firebase.FirebaseObjectStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;

public class GameRulesFirebaseObjectStorageAdapter extends FirebaseObjectStorageAdapterBase<GameRules> {
    private final static String PATH = "/GameRules";

    public GameRulesFirebaseObjectStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(PATH, db, gson::toJsonTree, jsonElement -> gson.fromJson(jsonElement, GameRules.class));
    }
}
