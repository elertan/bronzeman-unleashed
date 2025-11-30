package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.firebase.FirebaseKeyListStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroundItemOwnedByKeyListStorageAdapter
    extends FirebaseKeyListStorageAdapterBase<GroundItemOwnedByKey, GroundItemOwnedByData> {

    private static final String BASE_PATH = "/GroundItemOwnedBy";
    private static final Function<String, GroundItemOwnedByKey> stringToKey = GroundItemOwnedByKey::fromKey;
    private static final Function<GroundItemOwnedByKey, String> keyToString = GroundItemOwnedByKey::toKey;

    public GroundItemOwnedByKeyListStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(
            BASE_PATH, db, gson, stringToKey, keyToString, (jsonElement) -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return null;
                }
                return gson.fromJson(jsonElement, GroundItemOwnedByData.class);
            }
        );
    }
}
