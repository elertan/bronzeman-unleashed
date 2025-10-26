package com.elertan.remote.firebase.storageAdapters;

import com.elertan.event.BUEvent;
import com.elertan.event.BUEventGson;
import com.elertan.remote.firebase.FirebaseObjectStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;


public class LastEventFirebaseObjectStorageAdapter extends FirebaseObjectStorageAdapterBase<BUEvent> {
    private final static String PATH = "/LastEvent";

    public LastEventFirebaseObjectStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(PATH, db, buEvent -> BUEventGson.serialize(gson, buEvent), jsonElement -> BUEventGson.deserialize(gson, jsonElement));
    }
}
