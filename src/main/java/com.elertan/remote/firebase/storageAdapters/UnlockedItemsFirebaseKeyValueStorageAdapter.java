package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.UnlockedItem;
import com.elertan.remote.firebase.*;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class UnlockedItemsFirebaseKeyValueStorageAdapter extends FirebaseKeyValueStorageAdapterBase<Integer, UnlockedItem> {
    private final static String BASE_PATH = "/UnlockedItems";
    private final static Function<String, Integer> stringToKey = Integer::parseInt;
    private final static Function<Integer, String> keyToString = Object::toString;

    public UnlockedItemsFirebaseKeyValueStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(BASE_PATH, db, gson, stringToKey, keyToString, (jsonElement) -> {
            if (jsonElement == null || jsonElement.isJsonNull()) {
                return null;
            }

            return gson.fromJson(jsonElement, UnlockedItem.class);
        });
    }
}
