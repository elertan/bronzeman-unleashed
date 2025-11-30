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
        REGISTRY.put(BUEventType.SkillLevelUpAchievement, SkillLevelUpAchievementBUEvent.class);
        REGISTRY.put(BUEventType.TotalLevelAchievement, TotalLevelAchievementBUEvent.class);
        REGISTRY.put(BUEventType.CombatTaskAchievement, CombatTaskAchievementBUEvent.class);
        REGISTRY.put(
            BUEventType.DiaryCompletionAchievement,
            DiaryCompletionAchievementBUEvent.class
        );
        REGISTRY.put(
            BUEventType.QuestCompletionAchievement,
            QuestCompletionAchievementBUEvent.class
        );
        REGISTRY.put(BUEventType.ValuableLoot, ValuableLootBUEvent.class);
        REGISTRY.put(BUEventType.PetDrop, PetDropBUEvent.class);
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
