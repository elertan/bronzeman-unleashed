package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Value;

@Value
public class UnlockedItem {

    int id;
    String name;
    @JsonAdapter(AccountHashJsonAdapter.class)
    long acquiredByAccountHash;
    ISOOffsetDateTime acquiredAt;
    Integer droppedByNPCId;
}
