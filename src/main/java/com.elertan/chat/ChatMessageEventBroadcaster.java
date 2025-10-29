package com.elertan.chat;

import com.elertan.BUChatService;
import com.elertan.BUEventService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.MemberService;
import com.elertan.event.BUEvent;
import com.elertan.event.BUEventType;
import com.elertan.event.CombatLevelUpAchievementBUEvent;
import com.elertan.event.CombatTaskAchievementBUEvent;
import com.elertan.event.DiaryCompletionAchievementBUEvent;
import com.elertan.event.QuestCompletionAchievementBUEvent;
import com.elertan.event.SkillLevelUpAchievementBUEvent;
import com.elertan.event.TotalLevelAchievementBUEvent;
import com.elertan.models.Member;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageBuilder;

@Slf4j
@Singleton
public class ChatMessageEventBroadcaster implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private BUPluginConfig config;
    @Inject
    private BUEventService buEventService;
    @Inject
    private BUChatService buChatService;
    @Inject
    private MemberService memberService;
    private final Map<BUEventType, Function<BUEvent, String>> eventToChatMessageTransformers =
        ImmutableMap.<BUEventType, Function<BUEvent, String>>builder()
            .put(BUEventType.SkillLevelUpAchievement, this::transformSkillLevelUpAchievementEvent)
            .put(BUEventType.TotalLevelAchievement, this::transformTotalLevelAchievementEvent)
            .put(BUEventType.CombatLevelUpAchievement, this::transformCombatLevelUpAchievementEvent)
            .put(BUEventType.CombatTaskAchievement, this::transformCombatTaskAchievementEvent)
            .put(
                BUEventType.QuestCompletionAchievement,
                this::transformQuestCompletionAchievementEvent
            )
            .put(
                BUEventType.DiaryCompletionAchievement,
                this::transformDiaryCompletionAchievementEvent
            )
            .build();
    private final Consumer<BUEvent> eventListener = this::eventListener;

    @Override
    public void startUp() throws Exception {
        buEventService.addEventListener(eventListener);
    }

    @Override
    public void shutDown() throws Exception {
        buEventService.removeEventListener(eventListener);
    }

    private void eventListener(BUEvent event) {
        long fromAccountHash = event.getDispatchedFromAccountHash();

        if (client.getAccountHash() == fromAccountHash) {
            // This event is from us, so we should ignore it
            return;
        }

        BUEventType type = event.getType();
        Function<BUEvent, String> chatMessageTransformer = eventToChatMessageTransformers.get(type);
        if (chatMessageTransformer == null) {
            return;
        }
        String message = chatMessageTransformer.apply(event);
        if (message == null) {
//            log.warn("chat achievement event listener daemon has transformed event ({}) to message but message is null", type.name());
            return;
        }
        buChatService.sendMessage(message);
    }

    private String transformSkillLevelUpAchievementEvent(BUEvent event) {
        SkillLevelUpAchievementBUEvent e = (SkillLevelUpAchievementBUEvent) event;
        Member member = memberService.getMemberByAccountHash(e.getDispatchedFromAccountHash());
        if (member == null) {
            log.error(
                "could not find member by hash {} at transformSkillLevelUpAchievementEvent",
                e.getDispatchedFromAccountHash()
            );
            return null;
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());

        int level = e.getLevel();
        if (level == 99) {
            builder.append(" has reached the highest possible ");
            builder.append(e.getSkill());
            builder.append(" level of 99.");
            return builder.build();
        }

        builder.append(" has reached ");
        builder.append(e.getSkill());
        builder.append(" level ");
        builder.append(String.valueOf(level));
        builder.append(".");
        return builder.build();
    }

    private String transformTotalLevelAchievementEvent(BUEvent event) {
        TotalLevelAchievementBUEvent e = (TotalLevelAchievementBUEvent) event;
        Member member = memberService.getMemberByAccountHash(e.getDispatchedFromAccountHash());
        if (member == null) {
            log.error(
                "could not find member by hash {} at transformTotalLevelAchievementEvent",
                e.getDispatchedFromAccountHash()
            );
            return null;
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());
        builder.append(" has reached a total level of ");
        builder.append(String.valueOf(e.getTotalLevel()));
        builder.append(".");

        return builder.build();
    }

    private String transformCombatLevelUpAchievementEvent(BUEvent event) {
        CombatLevelUpAchievementBUEvent e = (CombatLevelUpAchievementBUEvent) event;
        Member member = memberService.getMemberByAccountHash(e.getDispatchedFromAccountHash());
        if (member == null) {
            log.error(
                "could not find member by hash {} at transformCombatLevelUpAchievementEvent",
                e.getDispatchedFromAccountHash()
            );
            return null;
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());

        int level = e.getLevel();
        if (level == 126) {
            builder.append(" has reached the highest possible combat level of 126.");
            return builder.build();
        }

        builder.append(" has reached combat level ");
        builder.append(String.valueOf(level));
        builder.append(".");
        return builder.build();
    }

    private String transformCombatTaskAchievementEvent(BUEvent event) {
        CombatTaskAchievementBUEvent e = (CombatTaskAchievementBUEvent) event;
        Member member = memberService.getMemberByAccountHash(e.getDispatchedFromAccountHash());
        if (member == null) {
            log.error(
                "could not find member by hash {} at transformCombatTaskAchievementEvent",
                e.getDispatchedFromAccountHash()
            );
            return null;
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());
        builder.append(" has completed a ");
        builder.append(e.getTier());
        builder.append(" combat task: ");
        builder.append(config.chatCombatTaskColor(), e.getName());

        return builder.build();
    }

    private String transformQuestCompletionAchievementEvent(BUEvent event) {
        QuestCompletionAchievementBUEvent e = (QuestCompletionAchievementBUEvent) event;
        Member member = memberService.getMemberByAccountHash(e.getDispatchedFromAccountHash());
        if (member == null) {
            log.error(
                "could not find member by hash {} at transformQuestCompletionAchievementEvent",
                e.getDispatchedFromAccountHash()
            );
            return null;
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());
        builder.append(" has completed a quest: ");
        builder.append(config.chatQuestNameColor(), e.getName());

        return builder.build();
    }


    private String transformDiaryCompletionAchievementEvent(BUEvent event) {
        DiaryCompletionAchievementBUEvent e = (DiaryCompletionAchievementBUEvent) event;
        Member member = memberService.getMemberByAccountHash(e.getDispatchedFromAccountHash());
        if (member == null) {
            log.error(
                "could not find member by hash {} at transformDiaryCompletionAchievementEvent",
                e.getDispatchedFromAccountHash()
            );
            return null;
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());
        builder.append(" has completed the ");
        builder.append(e.getTier());
        builder.append(" tier of the ");
        builder.append(e.getArea());
        builder.append(" diary.");

        return builder.build();
    }
}
