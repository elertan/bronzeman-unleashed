package com.elertan.data;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.remote.GroundItemOwnedByStoragePort;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.StorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GroundItemOwnedByDataProvider extends AbstractDataProvider {

    private final ConcurrentLinkedQueue<Listener> mapListeners = new ConcurrentLinkedQueue<>();

    @Inject
    private StorageService storageService;

    private GroundItemOwnedByStoragePort storagePort;
    private KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;

    /**
     * One {@link GroundItemOwnedByData} per {@link GroundItemOwnedByKey} (one Firebase object per pile).
     */
    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap;

    @Override
    protected StorageService getStorageService() {
        return storageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map) {
                ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> prior = groundItemOwnedByMap;
                groundItemOwnedByMap = new ConcurrentHashMap<>();
                if (map != null) {
                    for (Map.Entry<GroundItemOwnedByKey, GroundItemOwnedByData> e : map.entrySet()) {
                        GroundItemOwnedByKey k = e.getKey();
                        GroundItemOwnedByData remote = e.getValue();
                        GroundItemOwnedByData local = prior == null ? null : prior.get(k);
                        if (local != null && remote != null
                            && local.getWriteVersionOrZero() > remote.getWriteVersionOrZero()) {
                            groundItemOwnedByMap.put(k, local);
                        } else {
                            groundItemOwnedByMap.put(k, remote);
                        }
                    }
                }
                if (prior != null) {
                    for (Map.Entry<GroundItemOwnedByKey, GroundItemOwnedByData> e : prior.entrySet()) {
                        GroundItemOwnedByKey k = e.getKey();
                        if (!groundItemOwnedByMap.containsKey(k) && e.getValue().getWriteVersionOrZero() > 0) {
                            groundItemOwnedByMap.put(k, e.getValue());
                        }
                    }
                }

                for (Listener listener : mapListeners) {
                    try {
                        listener.onReadAll(groundItemOwnedByMap);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }

            @Override
            public void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                GroundItemOwnedByData current = groundItemOwnedByMap.get(key);
                if (current != null && value != null
                    && value.getWriteVersionOrZero() < current.getWriteVersionOrZero()) {
                    log.debug(
                        "Ignoring stale GroundItemOwnedBy remote update for {} (remoteVer={} localVer={})",
                        key,
                        value.getWriteVersionOrZero(),
                        current.getWriteVersionOrZero()
                    );
                    return;
                }

                groundItemOwnedByMap.put(key, value);

                for (Listener listener : mapListeners) {
                    try {
                        listener.onUpdate(key, value);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }

            @Override
            public void onDelete(GroundItemOwnedByKey key) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                groundItemOwnedByMap.remove(key);

                for (Listener listener : mapListeners) {
                    try {
                        listener.onDelete(key);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }
        };
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
        storagePort = storageService.getGroundItemOwnedByStoragePort();
        storagePort.addListener(storagePortListener);

        storagePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider storageport read all failed", throwable);
                return;
            }

            groundItemOwnedByMap = new ConcurrentHashMap<>();
            if (map != null) {
                groundItemOwnedByMap.putAll(map);
            }
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        groundItemOwnedByMap = null;
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            try {
                storagePort.close();
            } catch (Exception e) {
                log.error("Error closing storagePort", e);
            }
            storagePort = null;
        }
    }

    public void addMapListener(Listener listener) {
        mapListeners.add(listener);
    }

    public void removeMapListener(Listener listener) {
        mapListeners.remove(listener);
    }

    /**
     * {@link GroundItemOwnedByData#getQuantity()} is the amount to add (not the new total).
     */
    public CompletableFuture<Void> addToPileQuantity(GroundItemOwnedByKey key, GroundItemOwnedByData trustedDelta) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        GroundItemOwnedByData body = new GroundItemOwnedByData(
            trustedDelta.getAccountHash(),
            trustedDelta.getDespawnsAt(),
            trustedDelta.getQuantity(),
            trustedDelta.getDroppedByPlayerName(),
            null
        );
        return storagePort.transactionalAddQuantity(key, body).thenCompose(v -> syncLocalFromRead(key));
    }

    public CompletableFuture<Void> deletePile(GroundItemOwnedByKey key) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.transactionalDelete(key).thenCompose(v -> syncLocalFromRead(key));
    }

    public GroundItemOwnedByData getPile(GroundItemOwnedByKey key) {
        if (groundItemOwnedByMap == null) {
            return null;
        }
        return groundItemOwnedByMap.get(key);
    }

    public boolean hasEntries(GroundItemOwnedByKey key) {
        return getPile(key) != null;
    }

    public int getTotalOwnedQuantity(GroundItemOwnedByKey key) {
        GroundItemOwnedByData data = getPile(key);
        if (data == null) {
            return 0;
        }
        return data.getEntitlementQuantity();
    }

    public boolean hasActiveTakeClaimForAccount(GroundItemOwnedByKey key, long accountHash) {
        GroundItemOwnedByData data = getPile(key);
        if (data == null) {
            return false;
        }
        return data.hasActiveTakeClaimForAccount(accountHash, OffsetDateTime.now());
    }

    public CompletableFuture<Boolean> tryAcquireTakeClaim(
        GroundItemOwnedByKey key,
        long accountHash,
        ISOOffsetDateTime claimExpiresAt,
        String claimId
    ) {
        if (storagePort == null) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.transactionalAcquireTakeClaim(key, accountHash, claimExpiresAt, claimId)
            .thenCompose(acquired -> syncLocalFromRead(key).thenApply(__ -> acquired));
    }

    public CompletableFuture<Boolean> consumeQuantityWithTakeClaim(
        GroundItemOwnedByKey key,
        int quantity,
        long accountHash,
        String expectedClaimId
    ) {
        if (quantity <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        if (storagePort == null) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.transactionalConsumeQuantityWithTakeClaim(key, quantity, accountHash, expectedClaimId)
            .thenCompose(consumed -> syncLocalFromRead(key).thenApply(__ -> consumed));
    }

    public CompletableFuture<Void> consumeQuantity(GroundItemOwnedByKey key, int quantity) {
        if (quantity <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.transactionalConsumeQuantity(key, quantity)
            .thenCompose(v -> syncLocalFromRead(key));
    }

    private CompletableFuture<Void> syncLocalFromRead(GroundItemOwnedByKey key) {
        if (storagePort == null || groundItemOwnedByMap == null) {
            return CompletableFuture.completedFuture(null);
        }
        return storagePort.read(key).thenAccept(data -> {
            if (groundItemOwnedByMap == null) {
                return;
            }
            if (data == null) {
                groundItemOwnedByMap.remove(key);
            } else {
                groundItemOwnedByMap.put(key, data);
            }
        });
    }

    public interface Listener {
        void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map);

        void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value);

        void onDelete(GroundItemOwnedByKey key);
    }
}
