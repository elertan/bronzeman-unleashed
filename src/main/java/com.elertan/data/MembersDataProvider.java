package com.elertan.data;

import com.elertan.models.Member;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MembersDataProvider extends AbstractDataProvider {

    private final ConcurrentLinkedQueue<MemberMapListener> memberMapListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentSkipListSet<Long> optimisticallyAddedMembers = new ConcurrentSkipListSet<>();

    @Inject
    private RemoteStorageService remoteStorageService;

    private KeyValueStoragePort<Long, Member> keyValueStoragePort;
    private KeyValueStoragePort.Listener<Long, Member> storagePortListener;
    private ConcurrentHashMap<Long, Member> membersMap = new ConcurrentHashMap<>();

    public MembersDataProvider() {
        super("MembersDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
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
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
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

    @Override
    protected void onRemoteStorageNotReady() {
        membersMap = null;
        if (keyValueStoragePort != null) {
            keyValueStoragePort.removeListener(storagePortListener);
            keyValueStoragePort = null;
        }
    }

    public Map<Long, Member> getMembersMap() {
        if (membersMap == null) {
            return null;
        }
        return Collections.unmodifiableMap(membersMap);
    }

    public void addMemberMapListener(MemberMapListener listener) {
        memberMapListeners.add(listener);
    }

    public void removeMemberMapListener(MemberMapListener listener) {
        memberMapListeners.remove(listener);
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

    public interface MemberMapListener {
        void onUpdate(Member newMember, Member oldMember);
        void onDelete(Member member);
    }
}
