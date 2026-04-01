package com.elertan.remote;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import java.util.concurrent.CompletableFuture;

/**
 * Ground pile storage with optimistic-concurrency mutations (Firebase ETag CAS; local synchronized RMW).
 */
public interface GroundItemOwnedByStoragePort extends KeyValueStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> {

    /**
     * Decrements entitled quantity using compare-and-swap against the latest remote snapshot (no local map read).
     */
    CompletableFuture<Void> transactionalConsumeQuantity(GroundItemOwnedByKey key, int quantity);

    /**
     * Atomically increases entitled quantity using the latest remote value:
     * {@code newQty = remoteQty + trustedDelta.getQuantity()} (add at least 1).
     * Despawn time is the later of remote vs delta; dropped-by name is taken from delta if set else preserved.
     */
    CompletableFuture<Void> transactionalAddQuantity(GroundItemOwnedByKey key, GroundItemOwnedByData trustedDelta);

    /**
     * Deletes the pile if it still exists at the matched revision.
     */
    CompletableFuture<Void> transactionalDelete(GroundItemOwnedByKey key);
}
