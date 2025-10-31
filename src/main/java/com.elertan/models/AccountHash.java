package com.elertan.models;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@JsonAdapter(AccountHash.Adapter.class)
public class AccountHash {

    @Getter
    private long value;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AccountHash) {
            return ((AccountHash) obj).value == value;
        }
        return false;
    }

    public static final class Adapter extends TypeAdapter<AccountHash> {

        @Override
        public void write(JsonWriter out, AccountHash val) throws IOException {
            out.value(Long.toString(val.value));
        }

        @Override
        public AccountHash read(JsonReader in) throws IOException {
            return new AccountHash(Long.parseLong(in.nextString()));
        }
    }
}
