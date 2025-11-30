package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import javax.annotation.Nullable;
import lombok.Getter;

public class PetDropBUEvent extends BUEvent {

    /**
     * The item ID of the pet that was received.
     * Can be null in the Probita edge case (follower + full inventory).
     */
    @Getter
    @Nullable
    private final Integer petItemId;

    public PetDropBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime,
        @Nullable Integer petItemId) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.petItemId = petItemId;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.PetDrop;
    }
}
