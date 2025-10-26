package com.elertan.event;

import com.elertan.gson.AccountHashJsonAdapter;
import com.elertan.models.ISOOffsetDateTime;
import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;

public abstract class BUEvent {
    public abstract BUEventType getType();

    @JsonAdapter(AccountHashJsonAdapter.class)
    @Getter
    long dispatchedFromAccountHash;

    @Getter
    ISOOffsetDateTime timestamp;

    public BUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        this.dispatchedFromAccountHash = dispatchedFromAccountHash;
        this.timestamp = timestamp;
    }
}
