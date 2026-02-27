package com.elertan;

import com.elertan.data.MembersDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.elertan.models.MemberRole;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.elertan.utils.Subscription;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;

@Slf4j
@Singleton
public class MemberService implements BUPluginLifecycle {

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private AccountConfigurationService accountConfigurationService;
    @Inject private MembersDataProvider membersDataProvider;
    @Inject private BUChatService buChatService;
    @Inject private BUPluginConfig buPluginConfig;
    private Subscription accountConfigSubscription;
    private MembersDataProvider.MemberMapListener memberMapListener;

    @Override
    public void startUp() throws Exception {
        memberMapListener = new MembersDataProvider.MemberMapListener() {
            @Override
            public void onUpdate(Member member, Member old) {
                clientThread.invoke(() -> {
                    log.debug("member service -> member update: {} - old: {}",
                        member == null ? null : member.toString(),
                        old == null ? null : old.toString());
                    if (old == null) {
                        if (member.getAccountHash() != client.getAccountHash()) {
                            ChatMessageBuilder b = new ChatMessageBuilder();
                            b.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                            b.append(" has joined your Group Bronzeman.");
                            buChatService.sendMessage(b.build());
                        }
                    } else {
                        if (!Objects.equals(member.getName(), old.getName())) {
                            ChatMessageBuilder b = new ChatMessageBuilder();
                            b.append(buPluginConfig.chatPlayerNameColor(), old.getName());
                            b.append(" has changed their name to ");
                            b.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                            b.append(".");
                            buChatService.sendMessage(b.build());
                        }
                        if (member.getRole() != old.getRole()) {
                            ChatMessageBuilder b = new ChatMessageBuilder();
                            b.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                            b.append(" has changed their role from ");
                            b.append(buPluginConfig.chatHighlightColor(), old.getRole().toString());
                            b.append(" to ");
                            b.append(buPluginConfig.chatHighlightColor(), member.getRole().toString());
                            b.append(".");
                            buChatService.sendMessage(b.build());
                        }
                    }
                });
            }

            @Override
            public void onDelete(Member member) {
                clientThread.invoke(() -> {
                    ChatMessageBuilder b = new ChatMessageBuilder();
                    b.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                    b.append(" has left your Group Bronzeman.");
                    buChatService.sendMessage(b.build());
                });
            }
        };
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::currentAccountConfigurationChangeListener);
        membersDataProvider.addMemberMapListener(memberMapListener);
    }

    @Override
    public void shutDown() throws Exception {
        membersDataProvider.removeMemberMapListener(memberMapListener);
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
    }

    public Member getMemberByName(String playerName) {
        if (playerName == null) return null;
        if (membersDataProvider.getState().get() != MembersDataProvider.State.Ready)
            throw new IllegalStateException("Member data provider is not ready");
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null || membersMap.isEmpty()) return null;
        return membersMap.values().stream()
            .filter(m -> playerName.equalsIgnoreCase(m.getName()))
            .findFirst().orElse(null);
    }

    public Member getMemberByAccountHash(long accountHash) {
        if (membersDataProvider.getState().get() != MembersDataProvider.State.Ready)
            throw new IllegalStateException("Member data provider is not ready");
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        return membersMap == null ? null : membersMap.get(accountHash);
    }

    public Member getMyMember() {
        return getMemberByAccountHash(client.getAccountHash());
    }

    public boolean isPlayingAlone() {
        if (membersDataProvider.getState().get() != MembersDataProvider.State.Ready)
            throw new IllegalStateException("Member data provider is not ready");
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        return membersMap == null || membersMap.isEmpty() || membersMap.size() == 1;
    }

    private void currentAccountConfigurationChangeListener(AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) return;
        membersDataProvider.await(null).whenComplete((v, t) -> {
            if (t != null) {
                log.error("member service error whilst waiting till members data provider to become ready", t);
                return;
            }
            clientThread.invokeLater(this::whenMembersDataProviderReadyAfterAccountConfigurationSet);
        });
    }

    private void whenMembersDataProviderReadyAfterAccountConfigurationSet() {
        Player player = client.getLocalPlayer();
        String name = player.getName();
        if (name == null) {
            clientThread.invokeLater(this::whenMembersDataProviderReadyAfterAccountConfigurationSet);
            return;
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null) {
            log.error("member service error members map is null but should be init here");
            return;
        }
        long accountHash = client.getAccountHash();
        if (accountHash == -1) { log.error("member service error whilst getting account hash"); return; }

        log.debug("member service check if we need to add a member...");
        boolean shouldUpdateMember = false, shouldBeOwner = false;
        if (membersMap.isEmpty()) { shouldUpdateMember = true; shouldBeOwner = true; }
        else if (!membersMap.containsKey(accountHash)) { shouldUpdateMember = true; }
        else {
            Member member = membersMap.get(accountHash);
            String memberName = member.getName();
            if (memberName == null || !memberName.equals(name)) {
                log.info("member service -> name changed from '{}' to '{}' issue-ing member update", memberName, name);
                shouldUpdateMember = true;
            }
        }
        log.debug("should update member: {} - should be owner: {}", shouldUpdateMember, shouldBeOwner);

        if (shouldUpdateMember) {
            log.info("adding member...");
            MemberRole role = shouldBeOwner ? MemberRole.Owner : MemberRole.Member;
            Member member = new Member(accountHash, name, new ISOOffsetDateTime(OffsetDateTime.now()), role);
            membersDataProvider.addMember(member).whenComplete((v, t) -> {
                if (t != null) { log.error("member service error whilst adding member", t); return; }
                log.info("member added!");
            });
        }
    }

    public CompletableFuture<Void> promoteMemberToOwner(long accountHash) {
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null)
            return CompletableFuture.failedFuture(new IllegalStateException("members map is null"));
        Member memberToPromote = membersMap.get(accountHash);
        if (memberToPromote == null)
            return CompletableFuture.failedFuture(new IllegalStateException("member to promote is null"));
        if (memberToPromote.getRole() == MemberRole.Owner)
            return CompletableFuture.completedFuture(null);

        List<Member> membersToDemote = membersMap.values().stream()
            .filter(x -> x.getAccountHash() != accountHash && x.getRole() == MemberRole.Owner)
            .collect(Collectors.toList());
        Member promoted = new Member(
            memberToPromote.getAccountHash(), memberToPromote.getName(),
            memberToPromote.getJoinedAt(), MemberRole.Owner);

        return membersDataProvider.updateMember(promoted).thenCompose(v -> {
            List<CompletableFuture<Void>> demoteFutures = membersToDemote.stream()
                .map(m -> membersDataProvider.updateMember(
                    new Member(m.getAccountHash(), m.getName(), m.getJoinedAt(), MemberRole.Member)))
                .collect(Collectors.toList());
            return demoteFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(demoteFutures.toArray(new CompletableFuture[0]));
        });
    }

    public CompletableFuture<Void> leaveGroup() {
        Member myMember;
        try { myMember = getMyMember(); } catch (Exception e) { return CompletableFuture.failedFuture(e); }
        if (myMember == null) {
            log.warn("Attempted to leave group but we are not a member");
            return CompletableFuture.completedFuture(null);
        }
        return membersDataProvider.removeMember(myMember.getAccountHash())
            .whenComplete((v, t) -> { if (t != null) log.error("Failed to remove member", t); });
    }

    public CompletableFuture<Void> leaveGroupAndPromoteOldestMember() {
        Member myMember;
        try { myMember = getMyMember(); } catch (Exception e) { return CompletableFuture.failedFuture(e); }
        if (myMember == null) {
            log.warn("Attempted to leave group but we are not a member");
            return CompletableFuture.completedFuture(null);
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null)
            return CompletableFuture.failedFuture(new IllegalStateException("members map is null"));
        Optional<Member> oldest = membersMap.values().stream()
            .filter(x -> x.getAccountHash() != myMember.getAccountHash())
            .min(Comparator.comparing(x -> x.getJoinedAt().getValue()));
        if (!oldest.isPresent()) {
            log.warn("Attempted to leave group and promote but there are no other members");
            return CompletableFuture.completedFuture(null);
        }
        return promoteMemberToOwner(oldest.get().getAccountHash()).thenCompose(v -> leaveGroup());
    }
}
