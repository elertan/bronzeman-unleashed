package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.remote.GroundItemOwnedByStoragePort;
import com.elertan.remote.firebase.FirebaseKeyValueStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase.ConditionalWriteResult;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase.EtaggedSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroundItemOwnedByKeyValueStorageAdapter
    extends FirebaseKeyValueStorageAdapterBase<GroundItemOwnedByKey, GroundItemOwnedByData>
    implements GroundItemOwnedByStoragePort {

    /**
     * Upper bound for CAS retries during high-contention pile churn.
     */
    private static final int MAX_CAS_ATTEMPTS = 16;

    private final static String BASE_PATH = "/GroundItemOwnedBy";
    private final static Function<String, GroundItemOwnedByKey> stringToKey = GroundItemOwnedByKey::fromKey;
    private final static Function<GroundItemOwnedByKey, String> keyToString = GroundItemOwnedByKey::toKey;

    public GroundItemOwnedByKeyValueStorageAdapter(FirebaseRealtimeDatabase db, Gson gson) {
        super(
            BASE_PATH, db, gson, stringToKey, keyToString, (jsonElement) -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return null;
                }

                return gson.fromJson(jsonElement, GroundItemOwnedByData.class);
            }
        );
    }

    private GroundItemOwnedByData deserializePile(EtaggedSnapshot snapshot) {
        if (snapshot == null || snapshot.getData() == null || snapshot.getData().isJsonNull()) {
            return null;
        }
        return deserializeValue(snapshot.getData());
    }

    private static ISOOffsetDateTime mergeDespawnsAtLater(GroundItemOwnedByData cur, GroundItemOwnedByData delta) {
        if (delta.getDespawnsAt() == null) {
            return cur != null ? cur.getDespawnsAt() : null;
        }
        if (cur == null || cur.getDespawnsAt() == null) {
            return delta.getDespawnsAt();
        }
        OffsetDateTime tCur = cur.getDespawnsAt().getValue();
        OffsetDateTime tDelta = delta.getDespawnsAt().getValue();
        return tDelta.isAfter(tCur) ? delta.getDespawnsAt() : cur.getDespawnsAt();
    }

    /**
     * Bounded backoff between CAS retries to reduce immediate re-collisions on hot keys.
     */
    private <T> CompletableFuture<T> backoffThen(long attempt, java.util.concurrent.CompletableFuture<T> next) {
        if (attempt <= 0) {
            return next;
        }
        try {
            Thread.sleep(Math.min(40L, 2L + attempt * 2L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CompletableFuture<T> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        return next;
    }

    @Override
    public CompletableFuture<Void> transactionalConsumeQuantity(GroundItemOwnedByKey key, int quantity) {
        return consumeWithRetry(key, quantity, 0);
    }

    /**
     * Legacy/non-claim consume path using CAS to avoid lost updates during concurrent decrements.
     */
    private CompletableFuture<Void> consumeWithRetry(GroundItemOwnedByKey key, int quantity, int attempt) {
        if (attempt >= MAX_CAS_ATTEMPTS) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException(
                "GroundItemOwnedBy CAS consume exceeded retries for " + key));
            return f;
        }
        String path = childPath(key);
        return firebaseDb().getWithEtag(path).thenCompose(snap -> {
            GroundItemOwnedByData cur = deserializePile(snap);
            if (cur == null) {
                return CompletableFuture.completedFuture(null);
            }
            int entryQty = cur.getEntitlementQuantity();
            int newQty = Math.max(0, entryQty - quantity);
            String etag = snap.getEtag();
            Gson gson = firebaseGson();
            long nextVer = cur.getWriteVersionOrZero() + 1L;
            GroundItemOwnedByData next = new GroundItemOwnedByData(
                cur.getAccountHash(),
                cur.getDespawnsAt(),
                newQty,
                cur.getDroppedByPlayerName(),
                nextVer,
                cur.getTakeClaimedByAccountHash(),
                cur.getTakeClaimExpiresAt(),
                cur.getTakeClaimId()
            );
            JsonElement json = gson.toJsonTree(next);
            if (etag == null) {
                return firebaseDb().put(path, json).thenApply(__ -> null);
            }
            return firebaseDb().putConditional(path, json, etag).thenCompose(res -> {
                if (res == ConditionalWriteResult.PRECONDITION_FAILED) {
                    return backoffThen(attempt, consumeWithRetry(key, quantity, attempt + 1));
                }
                return CompletableFuture.completedFuture(null);
            });
        });
    }

    @Override
    public CompletableFuture<Void> transactionalAddQuantity(GroundItemOwnedByKey key, GroundItemOwnedByData trustedDelta) {
        return addWithRetry(key, trustedDelta, 0);
    }

    /**
     * CAS add path that preserves merged despawn metadata and survives contested write races.
     */
    private CompletableFuture<Void> addWithRetry(GroundItemOwnedByKey key, GroundItemOwnedByData delta, int attempt) {
        if (attempt >= MAX_CAS_ATTEMPTS) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException(
                "GroundItemOwnedBy CAS add exceeded retries for " + key));
            return f;
        }
        Integer dq = delta.getQuantity();
        int addQty = dq == null || dq < 1 ? 1 : dq;
        String path = childPath(key);
        return firebaseDb().getWithEtag(path).thenCompose(snap -> {
            GroundItemOwnedByData cur = deserializePile(snap);
            int newQty = (cur == null ? 0 : cur.getEntitlementQuantity()) + addQty;
            ISOOffsetDateTime mergedDespawn = mergeDespawnsAtLater(cur, delta);
            String droppedBy = delta.getDroppedByPlayerName() != null
                ? delta.getDroppedByPlayerName()
                : (cur == null ? null : cur.getDroppedByPlayerName());
            long nextVer = (cur == null ? 0L : cur.getWriteVersionOrZero()) + 1L;
            GroundItemOwnedByData stamped = new GroundItemOwnedByData(
                delta.getAccountHash(),
                mergedDespawn,
                newQty,
                droppedBy,
                nextVer,
                cur == null ? null : cur.getTakeClaimedByAccountHash(),
                cur == null ? null : cur.getTakeClaimExpiresAt(),
                cur == null ? null : cur.getTakeClaimId()
            );
            JsonElement json = firebaseGson().toJsonTree(stamped);
            String etag = snap.getEtag();
            if (etag == null) {
                return firebaseDb().put(path, json).thenApply(__ -> null);
            }
            return firebaseDb().putConditional(path, json, etag).thenCompose(res -> {
                if (res == ConditionalWriteResult.PRECONDITION_FAILED) {
                    return backoffThen(attempt, addWithRetry(key, delta, attempt + 1));
                }
                return CompletableFuture.completedFuture(null);
            });
        });
    }

    @Override
    public CompletableFuture<Void> transactionalDelete(GroundItemOwnedByKey key) {
        return deleteWithRetry(key, 0);
    }

    @Override
    public CompletableFuture<Boolean> transactionalAcquireTakeClaim(
        GroundItemOwnedByKey key,
        long claimantAccountHash,
        ISOOffsetDateTime claimExpiresAt,
        String claimId
    ) {
        return acquireTakeClaimWithRetry(key, claimantAccountHash, claimExpiresAt, claimId, 0);
    }

    /**
     * CAS claim-acquisition path.
     * Solves double-consume races by granting the lease to one active account at a time for a row.
     */
    private CompletableFuture<Boolean> acquireTakeClaimWithRetry(
        GroundItemOwnedByKey key,
        long claimantAccountHash,
        ISOOffsetDateTime claimExpiresAt,
        String claimId,
        int attempt
    ) {
        if (attempt >= MAX_CAS_ATTEMPTS) {
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException(
                "GroundItemOwnedBy CAS acquire-claim exceeded retries for " + key));
            return f;
        }

        String path = childPath(key);
        return firebaseDb().getWithEtag(path).thenCompose(snap -> {
            GroundItemOwnedByData cur = deserializePile(snap);
            if (cur == null) {
                return CompletableFuture.completedFuture(false);
            }

            OffsetDateTime now = OffsetDateTime.now();
            if (cur.hasAnyActiveTakeClaim(now)
                && (cur.getTakeClaimedByAccountHash() == null
                || cur.getTakeClaimedByAccountHash() != claimantAccountHash)) {
                return CompletableFuture.completedFuture(false);
            }

            long nextVer = cur.getWriteVersionOrZero() + 1L;
            GroundItemOwnedByData next = new GroundItemOwnedByData(
                cur.getAccountHash(),
                cur.getDespawnsAt(),
                cur.getQuantity(),
                cur.getDroppedByPlayerName(),
                nextVer,
                claimantAccountHash,
                claimExpiresAt,
                claimId
            );
            JsonElement json = firebaseGson().toJsonTree(next);
            String etag = snap.getEtag();
            if (etag == null) {
                return firebaseDb().put(path, json).thenApply(__ -> true);
            }
            return firebaseDb().putConditional(path, json, etag).thenCompose(res -> {
                if (res == ConditionalWriteResult.PRECONDITION_FAILED) {
                    return backoffThen(attempt, acquireTakeClaimWithRetry(
                        key,
                        claimantAccountHash,
                        claimExpiresAt,
                        claimId,
                        attempt + 1
                    ));
                }
                return CompletableFuture.completedFuture(true);
            });
        });
    }

    @Override
    public CompletableFuture<Boolean> transactionalConsumeQuantityWithTakeClaim(
        GroundItemOwnedByKey key,
        int quantity,
        long claimantAccountHash,
        String expectedClaimId
    ) {
        return consumeWithTakeClaimRetry(key, quantity, claimantAccountHash, expectedClaimId, 0);
    }

    /**
     * Claim-gated consume path used by pickup flows.
     * Solves extra-loot/over-consume incidents by requiring an active matching claim and refusing
     * decrements once entitlement has already reached zero.
     */
    private CompletableFuture<Boolean> consumeWithTakeClaimRetry(
        GroundItemOwnedByKey key,
        int quantity,
        long claimantAccountHash,
        String expectedClaimId,
        int attempt
    ) {
        if (attempt >= MAX_CAS_ATTEMPTS) {
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException(
                "GroundItemOwnedBy CAS consume-with-claim exceeded retries for " + key));
            return f;
        }

        String path = childPath(key);
        return firebaseDb().getWithEtag(path).thenCompose(snap -> {
            GroundItemOwnedByData cur = deserializePile(snap);
            if (cur == null) {
                return CompletableFuture.completedFuture(false);
            }
            OffsetDateTime now = OffsetDateTime.now();
            if (!cur.hasActiveTakeClaimForAccount(claimantAccountHash, now)) {
                return CompletableFuture.completedFuture(false);
            }
            if (expectedClaimId != null
                && cur.getTakeClaimId() != null
                && !expectedClaimId.equals(cur.getTakeClaimId())) {
                return CompletableFuture.completedFuture(false);
            }

            int entryQty = cur.getEntitlementQuantity();
            if (entryQty <= 0) {
                return CompletableFuture.completedFuture(false);
            }
            int newQty = Math.max(0, entryQty - Math.max(1, quantity));
            long nextVer = cur.getWriteVersionOrZero() + 1L;
            GroundItemOwnedByData next = new GroundItemOwnedByData(
                cur.getAccountHash(),
                cur.getDespawnsAt(),
                newQty,
                cur.getDroppedByPlayerName(),
                nextVer,
                null,
                null,
                null
            );

            JsonElement json = firebaseGson().toJsonTree(next);
            String etag = snap.getEtag();
            if (etag == null) {
                return firebaseDb().put(path, json).thenApply(__ -> true);
            }
            return firebaseDb().putConditional(path, json, etag).thenCompose(res -> {
                if (res == ConditionalWriteResult.PRECONDITION_FAILED) {
                    return backoffThen(attempt, consumeWithTakeClaimRetry(
                        key,
                        quantity,
                        claimantAccountHash,
                        expectedClaimId,
                        attempt + 1
                    ));
                }
                return CompletableFuture.completedFuture(true);
            });
        });
    }

    /**
     * CAS delete path for expiry/cleanup where concurrent writers may still be touching the row.
     */
    private CompletableFuture<Void> deleteWithRetry(GroundItemOwnedByKey key, int attempt) {
        if (attempt >= MAX_CAS_ATTEMPTS) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException(
                "GroundItemOwnedBy CAS delete exceeded retries for " + key));
            return f;
        }
        String path = childPath(key);
        return firebaseDb().getWithEtag(path).thenCompose(snap -> {
            GroundItemOwnedByData cur = deserializePile(snap);
            if (cur == null) {
                return CompletableFuture.completedFuture(null);
            }
            String etag = snap.getEtag();
            if (etag == null) {
                return firebaseDb().delete(path);
            }
            return firebaseDb().deleteConditional(path, etag).thenCompose(res -> {
                if (res == ConditionalWriteResult.PRECONDITION_FAILED) {
                    return backoffThen(attempt, deleteWithRetry(key, attempt + 1));
                }
                return CompletableFuture.completedFuture(null);
            });
        });
    }
}
