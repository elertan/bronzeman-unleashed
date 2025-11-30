package com.elertan.models;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@Builder
@AllArgsConstructor
@JsonAdapter(GroundItemOwnedByKey.Adapter.class)
public class GroundItemOwnedByKey implements Comparable<GroundItemOwnedByKey> {

    @Getter
    private final int itemId;
    @Getter
    private final int world;
    @Getter
    private final int worldViewId;
    @Getter
    private final int plane;
    @Getter
    private final int worldX;
    @Getter
    private final int worldY;

    public static GroundItemOwnedByKey of(int itemId, int world, int worldViewId, WorldPoint worldPoint) {
        return new GroundItemOwnedByKey(
            itemId,
            world,
            worldViewId,
            worldPoint.getPlane(),
            worldPoint.getX(),
            worldPoint.getY()
        );
    }

    public static GroundItemOwnedByKey fromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key is null");
        }
        String k = key;
        if (k.startsWith("{") && k.endsWith("}")) {
            k = k.substring(1, k.length() - 1);
        }
        String[] parts = k.split("_", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid key format: " + key);
        }
        try {
            int itemId = Integer.parseInt(parts[0]);
            int world = Integer.parseInt(parts[1]);
            int worldViewId = Integer.parseInt(parts[2]);
            int plane = Integer.parseInt(parts[3]);
            int worldX = Integer.parseInt(parts[4]);
            int worldY = Integer.parseInt(parts[5]);
            return new GroundItemOwnedByKey(itemId, world, worldViewId, plane, worldX, worldY);
        } catch (NumberFormatException e) {
            log.error("Invalid key format: {}", key, e);
            throw new IllegalArgumentException("Invalid key format: " + key, e);
        }
    }

    public String toKey() {
        // Emit the raw form used by fromKey(); add braces only for display if desired
        return String.format(
            "%d_%d_%d_%d_%d_%d",
            itemId,
            world,
            worldViewId,
            plane,
            worldX,
            worldY
        );
    }

    @Override
    public String toString() {
        String key = toKey();
        StringBuilder sb = new StringBuilder();
        sb.append("GroundItemOwnedByKey{");
        sb.append("itemId=").append(itemId);
        sb.append(", world=").append(world);
        sb.append(", worldViewId=").append(worldViewId);
        sb.append(", plane=").append(plane);
        sb.append(", worldX=").append(worldX);
        sb.append(", worldY=").append(worldY);
        sb.append(", key='").append(key).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroundItemOwnedByKey that = (GroundItemOwnedByKey) o;
        return itemId == that.itemId &&
            world == that.world &&
            worldViewId == that.worldViewId &&
            plane == that.plane &&
            worldX == that.worldX &&
            worldY == that.worldY;
    }

    @Override
    public int hashCode() {
        return toKey().hashCode();
    }

    @Override
    public int compareTo(GroundItemOwnedByKey o) {
        return toKey().compareTo(o.toKey());
    }


    public static final class Adapter extends TypeAdapter<GroundItemOwnedByKey> {

        @Override
        public void write(JsonWriter out, GroundItemOwnedByKey val) throws IOException {
            if (val == null) {
                out.nullValue();
                return;
            }
            out.value(val.toKey()); // raw key only
        }

        @Override
        public GroundItemOwnedByKey read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String text = in.nextString();
            return GroundItemOwnedByKey.fromKey(text);
        }
    }
}

