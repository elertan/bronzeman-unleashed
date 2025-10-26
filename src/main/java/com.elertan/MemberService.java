package com.elertan;

import com.elertan.data.MembersDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.elertan.models.MemberRole;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Singleton
public class MemberService implements BUPluginLifecycle {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private MembersDataProvider membersDataProvider;

    @Override
    public void startUp() throws Exception {
        accountConfigurationService.addCurrentAccountConfigurationChangeListener(this::currentAccountConfigurationChangeListener);
    }

    @Override
    public void shutDown() throws Exception {
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(this::currentAccountConfigurationChangeListener);
    }

    public Member getMemberByName(String playerName) {
        if (playerName == null) {
            return null;
        }
        if (membersDataProvider.getState() != MembersDataProvider.State.Ready) {
            throw new IllegalStateException("Member data provider is not ready");
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap.isEmpty()) {
            return null;
        }
        for (Member member : membersMap.values()) {
            String name = member.getName();
            if (playerName.equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    public Member getMemberByAccountHash(long accountHash) {
        if (membersDataProvider.getState() != MembersDataProvider.State.Ready) {
            throw new IllegalStateException("Member data provider is not ready");
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        return membersMap.get(accountHash);
    }

    public boolean isPlayingAlone() {
        if (membersDataProvider.getState() != MembersDataProvider.State.Ready) {
            throw new IllegalStateException("Member data provider is not ready");
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap.isEmpty()) {
            return true;
        }
        if (membersMap.size() == 1) {
            return true;
        }
        return false;
    }

    private void currentAccountConfigurationChangeListener(AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            return;
        }

        // When we have an account, we want to wait until the members are ready
        // if we have no members, we add ourselves as the owner
        // if we do have members, but not us, we add ourselves as a member
        membersDataProvider.waitUntilReady(null)
                .whenComplete((void1, waitUntilReadyThrowable) -> {
                    if (waitUntilReadyThrowable != null) {
                        log.error("member service error whilst waiting till members data provider to become ready", waitUntilReadyThrowable);
                        return;
                    }

                    clientThread.invokeLater(this::whenMembersDataProviderReadyAfterAccountConfigurationSet);
                });
    }

    private void whenMembersDataProviderReadyAfterAccountConfigurationSet() {
        Player player = client.getLocalPlayer();
        String name = player.getName();
        if (name == null) {
            // Wait till name gets set...
            clientThread.invokeLater(this::whenMembersDataProviderReadyAfterAccountConfigurationSet);
            return;
        }

        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null) {
            log.error("member service error members map is null but should be init here");
            return;
        }

        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            log.error("member service error whilst getting account hash");
            return;
        }

        log.info("member service check if we need to add a member...");


        boolean shouldUpdateMember = false;
        boolean shouldBeOwner = false;
        if (membersMap.isEmpty()) {
            shouldUpdateMember = true;
            shouldBeOwner = true;
        } else if (!membersMap.containsKey(accountHash)) {
            shouldUpdateMember = true;
        } else {
            Member member = membersMap.get(accountHash);
            String memberName = member.getName();
            if (memberName == null || !memberName.equals(name)) {
                log.info("member service -> name changed from '{}' to '{}' issue-ing member update", memberName, name);
                shouldUpdateMember = true;
            }
        }

        log.info("should update member: {} - should be owner: {}", shouldUpdateMember, shouldBeOwner);

        if (shouldUpdateMember) {
            log.info("adding member...");
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());

            MemberRole memberRole = shouldBeOwner ? MemberRole.Owner : MemberRole.Member;
            Member member = new Member(
                    accountHash,
                    name,
                    now,
                    memberRole
            );

            membersDataProvider.addMember(member).whenComplete((void2, addMemberThrowable) -> {
                if (addMemberThrowable != null) {
                    log.error("member service error whilst adding member", addMemberThrowable);
                    return;
                }
                log.info("member added!");
            });
        }
    }
}
