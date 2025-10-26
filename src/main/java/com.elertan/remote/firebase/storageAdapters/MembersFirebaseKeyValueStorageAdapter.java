package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.Member;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseKeyValueStorageAdapterBase;
import com.google.gson.Gson;

import java.util.function.Function;


public class MembersFirebaseKeyValueStorageAdapter extends FirebaseKeyValueStorageAdapterBase<Long, Member> {
    private final static String BASE_PATH = "/Members";
    private final static Function<String, Long> stringToKey = Long::parseLong;
    private final static Function<Long, String> keyToString = Object::toString;

    public MembersFirebaseKeyValueStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(BASE_PATH, db, gson, stringToKey, keyToString, (jsonElement) -> {
            if (jsonElement == null || jsonElement.isJsonNull()) {
                return null;
            }

            return gson.fromJson(jsonElement, Member.class);
        });
    }
}
