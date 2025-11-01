package com.elertan;

import com.elertan.data.MembersDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.elertan.models.MemberRole;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;

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
    private final Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;
    @Inject
    private BUChatService buChatService;
    @Inject
    private BUPluginConfig buPluginConfig;
    private MembersDataProvider.MemberMapListener memberMapListener;

    @Override
    public void startUp() throws Exception {
        memberMapListener = new MembersDataProvider.MemberMapListener() {
            @Override
            public void onUpdate(Member member, Member old) {
                log.info(
                    "member service -> member update: {} - old: {}",
                    member == null ? null : member.toString(),
                    old == null ? null : old.toString()
                );

                if (old == null) {
                    // If the updated member is not us, inform of a joi
                    // n
                    if (member.getAccountHash() != client.getAccountHash()) {
                        ChatMessageBuilder builder = new ChatMessageBuilder();
                        builder.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                        builder.append(" has joined your Group Bronzeman.");
                        buChatService.sendMessage(builder.build());
                    }
                } else {
                    if (!Objects.equals(member.getName(), old.getName())) {
                        ChatMessageBuilder builder = new ChatMessageBuilder();
                        builder.append(buPluginConfig.chatPlayerNameColor(), old.getName());
                        builder.append(" has name changed to ");
                        builder.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                        builder.append(".");
                        buChatService.sendMessage(builder.build());
                    }
                    if (member.getRole() != old.getRole()) {
                        ChatMessageBuilder builder = new ChatMessageBuilder();
                        builder.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                        builder.append(" has their role has changed from ");
                        builder.append(
                            buPluginConfig.chatHighlightColor(),
                            old.getRole().toString()
                        );
                        builder.append(" to ");
                        builder.append(
                            buPluginConfig.chatHighlightColor(),
                            member.getRole().toString()
                        );
                        builder.append(".");
                        buChatService.sendMessage(builder.build());
                    }
                }
            }

            @Override
            public void onDelete(Member member) {
                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                builder.append(" has left your Group Bronzeman.");
                buChatService.sendMessage(builder.build());
            }
        };

        accountConfigurationService.addCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);
        membersDataProvider.addMemberMapListener(memberMapListener);
    }

    @Override
    public void shutDown() throws Exception {
        membersDataProvider.removeMemberMapListener(memberMapListener);
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);
    }

    public Member getMemberByName(String playerName) {
        if (playerName == null) {
            return null;
        }
        if (membersDataProvider.getState() != MembersDataProvider.State.Ready) {
            throw new IllegalStateException("Member data provider is not ready");
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null || membersMap.isEmpty()) {
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
        if (membersMap == null) {
            return null;
        }
        return membersMap.get(accountHash);
    }

    public Member getMyMember() {
        return getMemberByAccountHash(client.getAccountHash());
    }

    public boolean isPlayingAlone() {
        if (membersDataProvider.getState() != MembersDataProvider.State.Ready) {
            throw new IllegalStateException("Member data provider is not ready");
        }
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null || membersMap.isEmpty()) {
            return true;
        }
        return membersMap.size() == 1;
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            return;
        }

        // When we have an account, we want to wait until the members are ready
        // if we have no members, we add ourselves as the owner
        // if we do have members, but not us, we add ourselves as a member
        membersDataProvider.waitUntilReady(null)
            .whenComplete((void1, waitUntilReadyThrowable) -> {
                if (waitUntilReadyThrowable != null) {
                    log.error(
                        "member service error whilst waiting till members data provider to become ready",
                        waitUntilReadyThrowable
                    );
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

        log.debug("member service check if we need to add a member...");

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
                log.info(
                    "member service -> name changed from '{}' to '{}' issue-ing member update",
                    memberName,
                    name
                );
                shouldUpdateMember = true;
            }
        }

        log.debug(
            "should update member: {} - should be owner: {}",
            shouldUpdateMember,
            shouldBeOwner
        );

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

    public CompletableFuture<Void> promoteMemberToOwner(long accountHash) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null) {
            future.completeExceptionally(new IllegalStateException("members map is null"));
            return future;
        }
        Member memberToPromote = membersMap.get(accountHash);
        if (memberToPromote == null) {
            future.completeExceptionally(new IllegalStateException("member to promote is null"));
            return future;
        }
        if (memberToPromote.getRole() == MemberRole.Owner) {
            future.complete(null);
            return future;
        }

        List<Member> membersToDemote = membersMap.values()
            .stream()
            .filter(x -> x.getAccountHash() != accountHash && x.getRole() == MemberRole.Owner)
            .collect(Collectors.toList());

        Member newMemberToPromote = new Member(
            memberToPromote.getAccountHash(),
            memberToPromote.getName(),
            memberToPromote.getJoinedAt(),
            MemberRole.Owner
        );
        membersDataProvider.updateMember(newMemberToPromote).whenComplete((void1, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            List<CompletableFuture<Void>> demoteFutures = new ArrayList<>();
            for (Member memberToDemote : membersToDemote) {
                Member newMemberToDemote = new Member(
                    memberToDemote.getAccountHash(),
                    memberToDemote.getName(),
                    memberToDemote.getJoinedAt(),
                    MemberRole.Member
                );
                CompletableFuture<Void> fut = membersDataProvider.updateMember(newMemberToDemote);
                demoteFutures.add(fut);
            }

            if (!demoteFutures.isEmpty()) {
                CompletableFuture.allOf(demoteFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((void2, throwable2) -> {
                        if (throwable2 != null) {
                            future.completeExceptionally(throwable2);
                            return;
                        }

                        future.complete(null);
                    });
                return;
            }

            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Void> leaveGroup() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Member myMember;
        try {
            myMember = getMyMember();
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }
        if (myMember == null) {
            log.warn("Attempted to leave group but we are not a member");
            future.complete(null);
            return future;
        }

        membersDataProvider.removeMember(myMember.getAccountHash())
            .whenComplete((void1, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to remove member", throwable);
                    future.completeExceptionally(throwable);
                    return;
                }

                future.complete(null);
            });

        return future;
    }

    public CompletableFuture<Void> leaveGroupAndPromoteOldestMember() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Member myMember;
        try {
            myMember = getMyMember();
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }
        if (myMember == null) {
            log.warn("Attempted to leave group but we are not a member");
            future.complete(null);
            return future;
        }

        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null) {
            future.completeExceptionally(new IllegalStateException("members map is null"));
            return future;
        }

        Optional<Member> otherOldestMemberOpt = membersMap.values()
            .stream()
            .filter(x -> x.getAccountHash() != myMember.getAccountHash())
            .min(Comparator.comparing(x -> x.getJoinedAt().getValue()));

        if (!otherOldestMemberOpt.isPresent()) {
            log.warn("Attempted to leave group and promote but there are no other members");
            future.complete(null);
            return future;
        }
        Member otherOldestMember = otherOldestMemberOpt.get();

        promoteMemberToOwner(otherOldestMember.getAccountHash()).whenComplete((void1, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            leaveGroup().whenComplete((void2, throwable2) -> {
                if (throwable2 != null) {
                    future.completeExceptionally(throwable2);
                    return;
                }

                future.complete(null);
            });
        });

        return future;
    }
}
