package com.elertan.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The locked items of an existing account. A list instead of a map, because Firebase turns objects
 * with numeric keys into arrays.
 */
@Value
public class StartingItems {

    ISOOffsetDateTime countedAt;
    List<Entry> items;

    public static StartingItems of(ISOOffsetDateTime countedAt, Map<Integer, Integer> quantities) {
        List<Entry> items = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                items.add(new Entry(entry.getKey(), entry.getValue()));
            }
        }
        return new StartingItems(countedAt, items);
    }

    public Map<Integer, Integer> toQuantityMap() {
        if (items == null) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> quantities = new HashMap<>();
        for (Entry entry : items) {
            quantities.merge(entry.getId(), entry.getQuantity(), Integer::sum);
        }
        return quantities;
    }

    @Value
    public static class Entry {

        int id;
        int quantity;
    }
}
