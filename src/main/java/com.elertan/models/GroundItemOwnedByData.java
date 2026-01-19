package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class GroundItemOwnedByData {

    @JsonAdapter(AccountHashJsonAdapter.class)
    private long accountHash;

    @NonNull
    private ISOOffsetDateTime despawnsAt;

    private String droppedByPlayerName;
}
