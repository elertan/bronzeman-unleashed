package com.elertan.remote.firebase;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractFirebaseStorageAdapter implements AutoCloseable {

    protected final String basePath;
    protected final FirebaseRealtimeDatabase db;
    private final Consumer<FirebaseSSE> sseListener = this::onSSE;

    protected AbstractFirebaseStorageAdapter(String basePath, FirebaseRealtimeDatabase db) {
        FirebaseRealtimeDatabase.validateBasePath(basePath);
        this.basePath = basePath;
        this.db = db;
        db.getStream().addServerSentEventListener(sseListener);
    }

    @Override
    public void close() throws Exception {
        db.getStream().removeServerSentEventListener(sseListener);
    }

    protected abstract void handleSSE(String[] pathParts, JsonElement data);

    private void onSSE(FirebaseSSE event) {
        if (event.getType() != FirebaseSSEType.Put) {
            return;
        }
        String path = event.getPath();
        if (!path.startsWith(basePath)) {
            return;
        }
        String[] pathParts = Arrays.stream(path.split("/"))
            .filter(part -> !part.isEmpty())
            .toArray(String[]::new);
        handleSSE(pathParts, event.getData());
    }

    protected static <L> void notifyAll(Collection<L> listeners, Consumer<L> action, String desc) {
        for (L listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.error("Failed to notify listener on {}", desc, e);
            }
        }
    }

    protected static <V> Map<String, V> parseJsonMap(JsonElement json, Function<JsonElement, V> deserializer) {
        if (json == null || json.isJsonNull() || !json.isJsonObject()) {
            return Collections.emptyMap();
        }
        JsonObject obj = json.getAsJsonObject();
        Map<String, V> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            V value = deserializer.apply(entry.getValue());
            if (value != null) {
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }
}
