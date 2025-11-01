package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@AllArgsConstructor
public class GroundItemOwnedByData {

    @Getter
    @Setter
    @JsonAdapter(AccountHashJsonAdapter.class)
    private long accountHash;

    @Getter
    @Setter
    @NonNull
    private ISOOffsetDateTime despawnsAt;

    @Getter
    @Setter
    private String droppedByPlayerName;
}
