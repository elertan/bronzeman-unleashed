package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.Member;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.ListenerUtils;
import com.elertan.utils.StateListenerManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MembersDataProvider implements BUPluginLifecycle {

    private final ConcurrentLinkedQueue<MemberMapListener> memberMapListeners = new ConcurrentLinkedQueue<>();
    private final StateListenerManager<State> stateListeners = new StateListenerManager<>("MembersDataProvider");
    private final ConcurrentSkipListSet<Long> optimisticallyAddedMembers = new ConcurrentSkipListSet<>();
    @Inject
    private RemoteStorageService remoteStorageService;
    private KeyValueStoragePort<Long, Member> keyValueStoragePort;
    private KeyValueStoragePort.Listener<Long, Member> storagePortListener;
    private ConcurrentHashMap<Long, Member> membersMap = new ConcurrentHashMap<>();
    @Getter
    private State state = State.NotReady;
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

        storagePortListener = new KeyValueStoragePort.Listener<Long, Member>() {
            @Override
            public void onFullUpdate(Map<Long, Member> map) {
                log.info("members data provider -> on full update");
                if (membersMap == null) {
                    return;
                }
                membersMap = new ConcurrentHashMap<>(map);
            }

            @Override
            public void onUpdate(Long key, Member member) {
                log.info("members data provider -> on update");

                if (membersMap == null) {
                    return;
                }
                Member oldMember;
                if (optimisticallyAddedMembers.contains(key)) {
                    optimisticallyAddedMembers.remove(key);
                    oldMember = null;
                } else {
                    oldMember = membersMap.get(key);
                }

                membersMap.put(key, member);

                for (MemberMapListener listener : memberMapListeners) {
                    try {
                        listener.onUpdate(member, oldMember);
                    } catch (Exception ex) {
                        log.error("membersUpdateListener: onUpdate", ex);
                    }
                }
            }

            @Override
            public void onDelete(Long key) {
                log.info("members data provider -> on delete");

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
            return null;
        }
        return Collections.unmodifiableMap(membersMap);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.addListener(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.removeListener(listener);
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

        optimisticallyAddedMembers.add(member.getAccountHash());
        membersMap.put(member.getAccountHash(), member);
        return keyValueStoragePort.update(member.getAccountHash(), member);
    }


    public CompletableFuture<Void> updateMember(Member member) {
        if (keyValueStoragePort == null) {
            throw new IllegalStateException("storagePort is null");
        }
        if (membersMap == null) {
            throw new IllegalStateException("membersMap is null");
        }

        membersMap.put(member.getAccountHash(), member);
        return keyValueStoragePort.update(member.getAccountHash(), member);
    }

    public CompletableFuture<Void> removeMember(long accountHash) {
        if (keyValueStoragePort == null) {
            throw new IllegalStateException("storagePort is null");
        }
        if (membersMap == null) {
            throw new IllegalStateException("membersMap is null");
        }

        return keyValueStoragePort.delete(accountHash);
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
            log.debug("MembersDataProvider initialized with {} members", membersMap.size());
            setState(State.Ready);
        });
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
        stateListeners.notifyListeners(state);
    }

    public enum State {
        NotReady,
        Ready,
    }

    public interface MemberMapListener {

        void onUpdate(Member newMember, Member oldMember);

        void onDelete(Member member);
    }
}
