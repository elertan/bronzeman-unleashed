package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@With
@AllArgsConstructor
public class Member {

    @JsonAdapter(AccountHashJsonAdapter.class)
    long accountHash;
    String name;
    ISOOffsetDateTime joinedAt;
    MemberRole role;
    // Null for members that joined before item locking existed: they are new accounts.
    StartMode startMode;

    public Member(long accountHash, String name, ISOOffsetDateTime joinedAt, MemberRole role) {
        this(accountHash, name, joinedAt, role, null);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", name, role);
    }
}
