package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ValuableLootBUEvent extends BUEvent {

    private final int itemId;
    private final long quantity;
    private final long pricePerItem;
    private final int npcId;

    public ValuableLootBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime,
        int itemId, long quantity, long pricePerItem, int npcId) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.itemId = itemId;
        this.quantity = quantity;
        this.pricePerItem = pricePerItem;
        this.npcId = npcId;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.ValuableLoot;
    }
}
