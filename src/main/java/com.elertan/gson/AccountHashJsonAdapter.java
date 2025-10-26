package com.elertan.gson;

import com.google.gson.*;

import java.lang.reflect.Type;

public class AccountHashJsonAdapter implements JsonSerializer<Long>, JsonDeserializer<Long> {
    @Override
    public JsonElement serialize(Long src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(String.valueOf(src));
    }

    @Override
    public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        return Long.parseLong(json.getAsString());
    }
}