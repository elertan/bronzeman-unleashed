package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import java.time.OffsetDateTime;
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

    /**
     * Increments on every local write to Firebase. Used to ignore stale SSE updates that would
     * resurrect quantity after a pickup (legacy JSON has no field {@code -> 0}).
     */
    private Long writeVersion;

    /**
     * Short-lived lease used to arbitrate contested take updates in shared piles.
     */
    private Long takeClaimedByAccountHash;

    /**
     * Expiry for the active take claim lease.
     */
    private ISOOffsetDateTime takeClaimExpiresAt;

    /**
     * Client-generated claim id for diagnostics and optional matching.
     */
    private String takeClaimId;

    public GroundItemOwnedByData(
        long accountHash,
        ISOOffsetDateTime despawnsAt,
        Integer quantity,
        String droppedByPlayerName,
        Long writeVersion
    ) {
        this(
            accountHash,
            despawnsAt,
            quantity,
            droppedByPlayerName,
            writeVersion,
            null,
            null,
            null
        );
    }

    /**
     * Bronzeman entitled count for policy and storage math. {@code null} still means 1 (legacy JSON);
     * explicit {@code 0} is allowed so a row can remain while blocking further loot after quota is met.
     */
    public int getEntitlementQuantity() {
        Integer q = quantity;
        if (q == null) {
            return 1;
        }
        return Math.max(0, q);
    }

    public long getWriteVersionOrZero() {
        return writeVersion == null ? 0L : writeVersion;
    }

    public boolean hasActiveTakeClaimForAccount(long accountHash, OffsetDateTime now) {
        if (takeClaimedByAccountHash == null || takeClaimExpiresAt == null || now == null) {
            return false;
        }
        return takeClaimedByAccountHash == accountHash && takeClaimExpiresAt.getValue().isAfter(now);
    }

    public boolean hasAnyActiveTakeClaim(OffsetDateTime now) {
        if (takeClaimedByAccountHash == null || takeClaimExpiresAt == null || now == null) {
            return false;
        }
        return takeClaimExpiresAt.getValue().isAfter(now);
    }
}
