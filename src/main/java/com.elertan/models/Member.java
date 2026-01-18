package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Value;

@Value
public class Member {

    @JsonAdapter(AccountHashJsonAdapter.class)
    long accountHash;
    String name;
    ISOOffsetDateTime joinedAt;
    MemberRole role;

    @Override
    public String toString() {
        return String.format("%s (%s)", name, role);
    }
}
