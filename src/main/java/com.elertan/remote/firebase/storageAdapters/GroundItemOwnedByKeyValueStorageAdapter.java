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

    private CompletableFuture<Void> backoffThen(long attempt, java.util.concurrent.CompletableFuture<Void> next) {
        if (attempt <= 0) {
            return next;
        }
        try {
            Thread.sleep(Math.min(40L, 2L + attempt * 2L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        return next;
    }

    @Override
    public CompletableFuture<Void> transactionalConsumeQuantity(GroundItemOwnedByKey key, int quantity) {
        return consumeWithRetry(key, quantity, 0);
    }

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
                nextVer
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
                nextVer
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
