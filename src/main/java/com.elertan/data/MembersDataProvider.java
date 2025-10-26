package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.Member;
import com.elertan.remote.RemoteStorageService;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.utils.ListenerUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class MembersDataProvider implements BUPluginLifecycle {
    public enum State {
        NotReady,
        Ready,
    }

    public interface MemberMapListener {
        void onUpdate(Member member, Member old);

        void onDelete(Member member);
    }

    @Inject
    private RemoteStorageService remoteStorageService;

    private KeyValueStoragePort<Long, Member> keyValueStoragePort;
    private KeyValueStoragePort.Listener<Long, Member> storagePortListener;

    private final ConcurrentLinkedQueue<MemberMapListener> memberMapListeners = new ConcurrentLinkedQueue<>();

    private ConcurrentHashMap<Long, Member> membersMap = new ConcurrentHashMap<>();

    @Getter
    private State state = State.NotReady;
    private final ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

        storagePortListener = new KeyValueStoragePort.Listener<Long, Member>() {
            @Override
            public void onFullUpdate(Map<Long, Member> map) {
                if (membersMap == null) {
                    return;
                }
                membersMap = new ConcurrentHashMap<>(map);
            }

            @Override
            public void onUpdate(Long key, Member value) {
                if (membersMap == null) {
                    return;
                }
                Member old = membersMap.get(key);
                membersMap.put(key, value);

                for (MemberMapListener listener : memberMapListeners) {
                    try {
                        listener.onUpdate(value, old);
                    } catch (Exception ex) {
                        log.error("membersUpdateListener: onUpdate", ex);
                    }
                }
            }

            @Override
            public void onDelete(Long key) {
                if (membersMap == null) {
                    return;
                }
                Member member = membersMap.get(key);
                membersMap.remove(key);

                for (MemberMapListener listener : memberMapListeners) {
                    try {
                        listener.onDelete(member);
                    } catch (Exception ex) {
                        log.error("membersDeleteListener: onDelete", ex);
                    }
                }
            }
        };

        tryInitialize();

    }

    @Override
    public void shutDown() throws Exception {
        state = State.NotReady;

        remoteStorageService.removeStateListener(remoteStorageServiceStateListener);
    }

    public Map<Long, Member> getMembersMap() {
        if (membersMap == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(membersMap);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    public void addMemberMapListener(MemberMapListener listener) {
        memberMapListeners.add(listener);
    }

    public void removeMemberMapListener(MemberMapListener listener) {
        memberMapListeners.remove(listener);
    }

    public CompletableFuture<Void> waitUntilReady(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            private Consumer<State> listener;

            @Override
            public boolean isReady() {
                return getState() == State.Ready;
            }

            @Override
            public void addListener(Runnable notify) {
                listener = state -> notify.run();
                addStateListener(listener);
            }

            @Override
            public void removeListener() {
                removeStateListener(listener);
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    public CompletableFuture<Void> addMember(Member member) {
        if (keyValueStoragePort == null) {
            throw new IllegalStateException("storagePort is null");
        }
        if (membersMap == null) {
            throw new IllegalStateException("membersMap is null");
        }

        membersMap.put(member.getAccountHash(), member);
        return keyValueStoragePort.update(member.getAccountHash(), member);
    }


    private void remoteStorageServiceStateListener(RemoteStorageService.State state) {
        if (state == RemoteStorageService.State.NotReady) {
            membersMap = null;
            keyValueStoragePort = null;
            setState(State.NotReady);
            return;
        }

        tryInitialize();
    }

    private void tryInitialize() {
        if (remoteStorageService.getState() == RemoteStorageService.State.NotReady) {
            membersMap = null;
            keyValueStoragePort = null;
            setState(State.NotReady);
            return;
        }

        keyValueStoragePort = remoteStorageService.getMembersStoragePort();
        keyValueStoragePort.addListener(storagePortListener);

        keyValueStoragePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("MembersDataProvider storageport read all failed", throwable);
                return;
            }

            membersMap = new ConcurrentHashMap<>(map);
            log.info("MembersDataProvider initialized with {} members", membersMap.size());
            setState(State.Ready);
        });
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;

        for (Consumer<State> listener : stateListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("set state listener unlocked item data provider error", e);
            }
        }
    }
}
