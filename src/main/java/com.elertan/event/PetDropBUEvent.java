package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import javax.annotation.Nullable;
import lombok.Getter;

public class PetDropBUEvent extends BUEvent {

    /**
     * The name of the pet that was received.
     * Can be null in the edge case where player has a follower and full inventory
     * (pet goes to Probita and we cannot identify which pet it was).
     */
    @Getter
    @Nullable
    private final String petName;

    public PetDropBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime,
        @Nullable String petName) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.petName = petName;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.PetDrop;
    }
}
