package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;
import lombok.Setter;

public class Member {
    @JsonAdapter(AccountHashJsonAdapter.class)
    @Getter
    @Setter
    private long accountHash;
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private ISOOffsetDateTime joinedAt;
    @Getter
    @Setter
    private MemberRole role;

    public Member(long accountHash, String name, ISOOffsetDateTime joinedAt, MemberRole role) {
        this.accountHash = accountHash;
        this.name = name;
        this.joinedAt = joinedAt;
        this.role = role;
    }
}
