package com.elertan.remote.firebase;

import com.google.gson.JsonElement;
import lombok.Getter;

import java.util.Objects;

@Getter
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
        return "FirebaseSseEvent{type='" + type.raw() + "', path='" + path + "', data=" + data + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FirebaseSSE)) return false;
        FirebaseSSE that = (FirebaseSSE) o;
        return Objects.equals(type, that.type) && Objects.equals(path, that.path) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, data);
    }
}