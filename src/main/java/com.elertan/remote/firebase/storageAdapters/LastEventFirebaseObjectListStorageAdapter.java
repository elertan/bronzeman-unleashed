package com.elertan.remote.firebase.storageAdapters;

import com.elertan.event.BUEvent;
import com.elertan.event.BUEventGson;
import com.elertan.remote.firebase.FirebaseObjectListStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;

public class LastEventFirebaseObjectListStorageAdapter extends
    FirebaseObjectListStorageAdapterBase<BUEvent> {

    private final static String PATH = "/LastEvent";

    public LastEventFirebaseObjectListStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(
            PATH,
            db,
            buEvent -> BUEventGson.serialize(gson, buEvent),
            jsonElement -> BUEventGson.deserialize(gson, jsonElement)
        );
    }
}
