package com.elertan.event;

import static org.junit.Assert.assertEquals;

import com.elertan.models.ISOOffsetDateTime;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.OffsetDateTime;
import org.junit.Test;

public class ValuableLootBUEventGsonTest {

    private final Gson gson = new Gson();

    @Test
    public void deserializesEventsStoredWithIntFields() {
        // Shape written by 0.2.0 and earlier, where quantity and pricePerItem were ints
        JsonElement stored = new JsonParser().parse("{\"type\":\"ValuableLoot\",\"data\":{"
            + "\"itemId\":4151,\"quantity\":3,\"pricePerItem\":1500000,\"npcId\":415,"
            + "\"dispatchedFromAccountHash\":123}}");

        ValuableLootBUEvent event = (ValuableLootBUEvent) BUEventGson.deserialize(gson, stored);

        assertEquals(4151, event.getItemId());
        assertEquals(3L, event.getQuantity());
        assertEquals(1_500_000L, event.getPricePerItem());
        assertEquals(415, event.getNpcId());
    }

    @Test
    public void roundTripsValuesBeyondIntRange() {
        long quantity = 5_000_000_000L;
        ValuableLootBUEvent original = new ValuableLootBUEvent(
            123L, new ISOOffsetDateTime(OffsetDateTime.now()), 995, quantity, 1L, 415);

        JsonElement json = BUEventGson.serialize(gson, original);
        ValuableLootBUEvent restored = (ValuableLootBUEvent) BUEventGson.deserialize(gson, json);

        assertEquals(quantity, restored.getQuantity());
        assertEquals(1L, restored.getPricePerItem());
    }

    @Test
    public void newEventsRemainReadableByIntFieldClients() {
        ValuableLootBUEvent event = new ValuableLootBUEvent(
            123L, new ISOOffsetDateTime(OffsetDateTime.now()), 4151, 3L, 1_500_000L, 415);

        JsonObject data = BUEventGson.serialize(gson, event).getAsJsonObject()
            .getAsJsonObject("data");
        LegacyValuableLoot legacy = gson.fromJson(data, LegacyValuableLoot.class);

        assertEquals(3, legacy.quantity);
        assertEquals(1_500_000, legacy.pricePerItem);
    }

    // Mirrors the 0.2.0 field types that group members on older versions still use
    private static final class LegacyValuableLoot {
        int quantity;
        int pricePerItem;
    }
}
