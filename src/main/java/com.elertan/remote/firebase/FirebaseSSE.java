package com.elertan.remote.firebase;

import com.google.gson.JsonElement;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class FirebaseSSE {

    private final FirebaseSSEType type;
    private final String path;
    private final JsonElement data;

    public FirebaseSSE(FirebaseSSEType type, String path, JsonElement data) {
        this.type = type;
        this.path = path;
        this.data = data;
    }

    @Override
    public String toString() {
        return "FirebaseSseEvent{type='" + type.raw() + "', path='" + path + "', data=" + data
            + "}";
    }

}