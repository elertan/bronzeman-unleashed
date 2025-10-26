package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;
import lombok.Setter;

public class UnlockedItem {
    @Getter
    @Setter
    private int id;
    @Getter
    @Setter
    private String name;
    @JsonAdapter(AccountHashJsonAdapter.class)
    @Getter
    @Setter
    private long acquiredByAccountHash;
    @Getter
    @Setter
    private ISOOffsetDateTime acquiredAt;
    @Getter
    @Setter
    private Integer droppedByNPCId;

    public UnlockedItem(int id, String name, long acquiredByAccountHash, ISOOffsetDateTime acquiredAt, Integer droppedByNPCId) {
        this.id = id;
        this.name = name;
        this.acquiredByAccountHash = acquiredByAccountHash;
        this.acquiredAt = acquiredAt;
        this.droppedByNPCId = droppedByNPCId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UnlockedItem{");
        sb.append("id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", acquiredByAccountHash=").append(acquiredByAccountHash);
        sb.append(", acquiredAt=").append(acquiredAt);
        sb.append(", droppedByNPCId=").append(droppedByNPCId);
        sb.append('}');
        return sb.toString();
    }
}
