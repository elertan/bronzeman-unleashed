package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class PetDropBUEvent extends BUEvent {

    /**
     * The item ID of the pet that was received.
     * Can be null in the Probita edge case (follower + full inventory).
     */
    @Nullable
    private final Integer petItemId;

    /**
     * Whether this is a duplicate pet (player already has this pet).
     */
    private final boolean isDuplicate;

    public PetDropBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime,
        @Nullable Integer petItemId, boolean isDuplicate) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.petItemId = petItemId;
        this.isDuplicate = isDuplicate;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.PetDrop;
    }
}
