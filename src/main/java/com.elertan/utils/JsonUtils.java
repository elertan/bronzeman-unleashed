package com.elertan.utils;

import com.google.gson.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class JsonUtils {
    public static Object jsonElementToNative(JsonElement el) throws IllegalArgumentException {
        if (el == null || el.isJsonNull()) {
            return null;
        }

        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber()) {
                // Prefer integral types when possible, else fall back to double
                BigDecimal bd = p.getAsBigDecimal();
                try {
                    if (bd.scale() <= 0) { // no fractional part
                        try {
                            return bd.intValueExact();
                        } catch (ArithmeticException ignored) {
                            try {
                                return bd.longValueExact();
                            } catch (ArithmeticException ignored2) {
                                return bd.toBigIntegerExact();
                            }
                        }
                    }
                } catch (ArithmeticException ignored3) {
                    // fall through to double
                }
                return bd.doubleValue();
            }
            if (p.isString()) {
                return p.getAsString();
            }
        }

        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            ArrayList<Object> list = new ArrayList<>(arr.size());
            for (JsonElement e : arr) {
                list.add(jsonElementToNative(e));
            }
            return list;
        }

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), jsonElementToNative(entry.getValue()));
            }
            return map;
        }

        throw new IllegalArgumentException("Unsupported JsonElement: " + el);
    }
}
