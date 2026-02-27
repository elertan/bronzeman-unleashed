package com.elertan.models;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@Builder
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@JsonAdapter(GroundItemOwnedByKey.Adapter.class)
public class GroundItemOwnedByKey implements Comparable<GroundItemOwnedByKey> {

    private final int itemId;
    private final int world;
    private final int worldViewId;
    private final int plane;
    private final int worldX;
    private final int worldY;

    public static GroundItemOwnedByKey of(int itemId, int world, int worldViewId, WorldPoint wp) {
        return new GroundItemOwnedByKey(itemId, world, worldViewId, wp.getPlane(), wp.getX(), wp.getY());
    }

    public static GroundItemOwnedByKey fromKey(String key) {
        if (key == null) throw new IllegalArgumentException("Key is null");
        String k = key;
        if (k.startsWith("{") && k.endsWith("}")) k = k.substring(1, k.length() - 1);
        String[] parts = k.split("_", -1);
        if (parts.length != 6) throw new IllegalArgumentException("Invalid key format: " + key);
        try {
            return new GroundItemOwnedByKey(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
        } catch (NumberFormatException e) {
            log.error("Invalid key format: {}", key, e);
            throw new IllegalArgumentException("Invalid key format: " + key, e);
        }
    }

    public String toKey() {
        return String.format("%d_%d_%d_%d_%d_%d", itemId, world, worldViewId, plane, worldX, worldY);
    }

    @Override
    public String toString() {
        return "GroundItemOwnedByKey{itemId=" + itemId + ", world=" + world
            + ", worldViewId=" + worldViewId + ", plane=" + plane
            + ", worldX=" + worldX + ", worldY=" + worldY
            + ", key='" + toKey() + "'}";
    }

    @Override
    public int compareTo(GroundItemOwnedByKey o) { return toKey().compareTo(o.toKey()); }

    public static final class Adapter extends TypeAdapter<GroundItemOwnedByKey> {
        @Override
        public void write(JsonWriter out, GroundItemOwnedByKey val) throws IOException {
            if (val == null) { out.nullValue(); return; }
            out.value(val.toKey());
        }

        @Override
        public GroundItemOwnedByKey read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return null; }
            return GroundItemOwnedByKey.fromKey(in.nextString());
        }
    }
}
