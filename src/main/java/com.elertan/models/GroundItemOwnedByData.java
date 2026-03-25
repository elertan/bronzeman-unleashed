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

    /**
     * Stack size for this Firebase entry. Null or absent in JSON means 1 (legacy rows).
     */
    private Integer quantity;

    private String droppedByPlayerName;

    public int getQuantityOrDefaultOne() {
        Integer q = quantity;
        return q == null || q < 1 ? 1 : q;
    }
}
