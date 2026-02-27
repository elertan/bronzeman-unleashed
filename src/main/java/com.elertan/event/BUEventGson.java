package com.elertan.event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.EnumMap;

public class BUEventGson {

    private static final EnumMap<BUEventType, Class<? extends BUEvent>> REGISTRY = new EnumMap<>(
        BUEventType.class);

    static {
        Object[][] entries = {
            {BUEventType.SkillLevelUpAchievement, BUEvent.SkillLevelUpAchievementBUEvent.class},
            {BUEventType.TotalLevelAchievement, BUEvent.TotalLevelAchievementBUEvent.class},
            {BUEventType.CombatTaskAchievement, BUEvent.CombatTaskAchievementBUEvent.class},
            {BUEventType.QuestCompletionAchievement, BUEvent.QuestCompletionAchievementBUEvent.class},
            {BUEventType.DiaryCompletionAchievement, BUEvent.DiaryCompletionAchievementBUEvent.class},
            {BUEventType.CollectionLogUnlockAchievement, BUEvent.CollectionLogUnlockAchievementBUEvent.class},
            {BUEventType.ValuableLoot, BUEvent.ValuableLootBUEvent.class},
            {BUEventType.PetDrop, BUEvent.PetDropBUEvent.class},
        };
        for (Object[] e : entries) {
            @SuppressWarnings("unchecked")
            Class<? extends BUEvent> clazz = (Class<? extends BUEvent>) e[1];
            REGISTRY.put((BUEventType) e[0], clazz);
        }
    }

    public static JsonElement serialize(Gson gson, BUEvent value) {
        JsonObject obj = new JsonObject();
        obj.add("type", new JsonPrimitive(value.getType().name()));
        obj.add("data", gson.toJsonTree(value));
        return obj;
    }

    public static BUEvent deserialize(Gson gson, JsonElement jsonElement) {
        if (jsonElement.isJsonNull()) {
            return null;
        }
        JsonObject obj = jsonElement.getAsJsonObject();
        if (obj == null) {
            return null;
        }

        String eventTypeStr = obj.get("type").getAsJsonPrimitive().getAsString();
        BUEventType eventType = BUEventType.valueOf(eventTypeStr);
        JsonObject data = obj.get("data").getAsJsonObject();

        Class<? extends BUEvent> type = REGISTRY.get(eventType);
        if (type == null) {
            throw new IllegalStateException(
                "Registration of type " + eventType + " was not registered");
        }

        return gson.fromJson(data, type);
    }
}
