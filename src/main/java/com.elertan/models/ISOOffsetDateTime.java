package com.elertan.models;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@JsonAdapter(ISOOffsetDateTime.Adapter.class)
public final class ISOOffsetDateTime {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    @Getter
    private final OffsetDateTime value;

    public ISOOffsetDateTime(OffsetDateTime value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return FORMATTER.format(value);
    }

    public static final class Adapter extends TypeAdapter<ISOOffsetDateTime> {
        @Override
        public void write(JsonWriter out, ISOOffsetDateTime val) throws IOException {
            if (val == null || val.value == null) {
                out.nullValue();
                return;
            }
            out.value(FORMATTER.format(val.value));
        }

        @Override
        public ISOOffsetDateTime read(JsonReader in) throws IOException {
            String text = in.nextString();
            return new ISOOffsetDateTime(OffsetDateTime.parse(text, FORMATTER));
        }
    }
}