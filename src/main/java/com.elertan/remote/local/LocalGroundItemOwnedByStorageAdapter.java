package com.elertan.remote.local;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.ISOOffsetDateTime;
import java.time.OffsetDateTime;
import com.elertan.remote.GroundItemOwnedByStoragePort;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.local.LocalStorageAdapters.InMemoryKeyValueStorageAdapter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory ground piles with serialized compare-and-swap semantics (for solo/local parity with Firebase CAS).
 */
public final class LocalGroundItemOwnedByStorageAdapter implements GroundItemOwnedByStoragePort {

    private final InMemoryKeyValueStorageAdapter<GroundItemOwnedByKey, GroundItemOwnedByData> inner = new InMemoryKeyValueStorageAdapter<>();
    private final Object txLock = new Object();

    @Override
    public CompletableFuture<GroundItemOwnedByData> read(GroundItemOwnedByKey key) {
        return inner.read(key);
    }

    @Override
    public CompletableFuture<Map<GroundItemOwnedByKey, GroundItemOwnedByData>> readAll() {
        return inner.readAll();
    }

    @Override
    public CompletableFuture<Void> update(GroundItemOwnedByKey key, GroundItemOwnedByData value) {
        return inner.update(key, value);
    }

    @Override
    public CompletableFuture<Void> updateAll(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map) {
        return inner.updateAll(map);
    }

    @Override
    public CompletableFuture<Void> delete(GroundItemOwnedByKey key) {
        return inner.delete(key);
    }

    @Override
    public void addListener(KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> listener) {
        inner.addListener(listener);
    }

    @Override
    public void removeListener(KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> listener) {
        inner.removeListener(listener);
    }

    @Override
    public void close() {
        inner.close();
    }

    @Override
    public CompletableFuture<Void> transactionalConsumeQuantity(GroundItemOwnedByKey key, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (txLock) {
                GroundItemOwnedByData cur = inner.read(key).join();
                if (cur == null) {
                    return null;
                }
                int entryQty = cur.getEntitlementQuantity();
                int newQty = Math.max(0, entryQty - quantity);
                long nextVer = cur.getWriteVersionOrZero() + 1L;
                GroundItemOwnedByData next = new GroundItemOwnedByData(
                    cur.getAccountHash(),
                    cur.getDespawnsAt(),
                    newQty,
                    cur.getDroppedByPlayerName(),
                    nextVer
                );
                inner.update(key, next).join();
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Void> transactionalAddQuantity(GroundItemOwnedByKey key, GroundItemOwnedByData trustedDelta) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (txLock) {
                GroundItemOwnedByData cur = inner.read(key).join();
                Integer dq = trustedDelta.getQuantity();
                int addQty = dq == null || dq < 1 ? 1 : dq;
                int newQty = (cur == null ? 0 : cur.getEntitlementQuantity()) + addQty;
                ISOOffsetDateTime mergedDespawn = mergeDespawnsAtLater(cur, trustedDelta);
                String droppedBy = trustedDelta.getDroppedByPlayerName() != null
                    ? trustedDelta.getDroppedByPlayerName()
                    : (cur == null ? null : cur.getDroppedByPlayerName());
                long nextVer = (cur == null ? 0L : cur.getWriteVersionOrZero()) + 1L;
                GroundItemOwnedByData stamped = new GroundItemOwnedByData(
                    trustedDelta.getAccountHash(),
                    mergedDespawn,
                    newQty,
                    droppedBy,
                    nextVer
                );
                inner.update(key, stamped).join();
                return null;
            }
        });
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

    @Override
    public CompletableFuture<Void> transactionalDelete(GroundItemOwnedByKey key) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (txLock) {
                inner.delete(key).join();
                return null;
            }
        });
    }
}
