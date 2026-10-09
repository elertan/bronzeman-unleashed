package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.StartingItems;
import com.elertan.remote.firebase.FirebaseKeyValueStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;
import java.util.function.Function;

public class StartingItemsFirebaseKeyValueStorageAdapter extends
    FirebaseKeyValueStorageAdapterBase<Long, StartingItems> {

    private final static String BASE_PATH = "/StartingItems";
    private final static Function<String, Long> stringToKey = Long::parseLong;
    private final static Function<Long, String> keyToString = Object::toString;

    public StartingItemsFirebaseKeyValueStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(
            BASE_PATH, db, gson, stringToKey, keyToString, (jsonElement) -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return null;
                }

                return gson.fromJson(jsonElement, StartingItems.class);
            }
        );
    }
}
