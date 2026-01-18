package com.elertan.chat;

import com.elertan.BUChatService;
import com.elertan.BUEventService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.MemberService;
import com.elertan.event.*;
import com.elertan.models.Member;
import com.elertan.utils.AsyncUtils;
import com.google.common.collect.ImmutableMap;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageBuilder;

import static com.elertan.utils.AsyncUtils.withErrorLogging;

@Slf4j
@Singleton
public class ChatMessageEventBroadcaster implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private AsyncUtils asyncUtils;
    @Inject
    private BUPluginConfig config;
    @Inject
    private BUEventService buEventService;
    @Inject
    private BUChatService buChatService;
    @Inject
    private MemberService memberService;

    private final Map<BUEventType, BiFunction<BUEvent, Member, CompletableFuture<String>>>
        eventToChatMessageTransformers =
        ImmutableMap.<BUEventType, BiFunction<BUEvent, Member, CompletableFuture<String>>>builder()
            .put(BUEventType.SkillLevelUpAchievement, this::transformSkillLevelUpAchievementEvent)
            .put(BUEventType.TotalLevelAchievement, this::transformTotalLevelAchievementEvent)
            .put(BUEventType.CombatLevelUpAchievement, this::transformCombatLevelUpAchievementEvent)
            .put(BUEventType.CombatTaskAchievement, this::transformCombatTaskAchievementEvent)
            .put(BUEventType.QuestCompletionAchievement, this::transformQuestCompletionAchievementEvent)
            .put(BUEventType.DiaryCompletionAchievement, this::transformDiaryCompletionAchievementEvent)
            .put(BUEventType.CollectionLogUnlockAchievement, this::transformCollectionLogAchievementEvent)
            .put(BUEventType.ValuableLoot, this::transformValuableLootEvent)
            .put(BUEventType.PetDrop, this::transformPetDropEvent)
            .build();

    private Subscription eventSubscription;

    @Override
    public void startUp() throws Exception {
        eventSubscription = buEventService.getLastEvent().subscribe(this::onEvent);
    }

    @Override
    public void shutDown() throws Exception {
        if (eventSubscription != null) {
            eventSubscription.dispose();
            eventSubscription = null;
        }
    }

    private void onEvent(BUEvent event) {
        BUEventType type = event.getType();
        BiFunction<BUEvent, Member, CompletableFuture<String>> chatMessageTransformer =
            eventToChatMessageTransformers.get(type);
        if (chatMessageTransformer == null) {
            return;
        }

        Member member = memberService.getMemberByAccountHash(event.getDispatchedFromAccountHash());
        if (member == null) {
            log.error("could not find member by hash {}", event.getDispatchedFromAccountHash());
            return;
        }

        // Skip if the event was dispatched from the same account
        asyncUtils.runOnClientThread(() -> client.getAccountHash() == event.getDispatchedFromAccountHash())
            .thenAccept((sameAccount) -> {
                if (sameAccount) {
                    return;
                }

                // Transform the event to a message and send it
                CompletableFuture<String> messageFuture = chatMessageTransformer.apply(event, member);
                withErrorLogging(messageFuture, "Failed to transform event to chat message")
                    .thenAccept((message) ->
                        Optional.ofNullable(message).ifPresent(buChatService::sendMessage));
            });
    }

    private ChatMessageBuilder getChatMessagePlayerNameBuilder(Member member) {
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatPlayerNameColor(), member.getName());
        return builder;
    }

    private CompletableFuture<String> transformSkillLevelUpAchievementEvent(BUEvent event, Member member) {
        SkillLevelUpAchievementBUEvent e = (SkillLevelUpAchievementBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);

        int level = e.getLevel();
        if (level == 99) {
            builder.append(" has reached the highest possible ");
            builder.append(e.getSkill());
            builder.append(" level of 99.");
            return CompletableFuture.completedFuture(builder.build());
        }

        builder.append(" has reached ");
        builder.append(e.getSkill());
        builder.append(" level ");
        builder.append(String.valueOf(level));
        builder.append(".");
        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformTotalLevelAchievementEvent(BUEvent event, Member member) {
        TotalLevelAchievementBUEvent e = (TotalLevelAchievementBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);
        builder.append(" has reached a total level of ");
        builder.append(String.valueOf(e.getTotalLevel()));
        builder.append(".");

        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformCombatLevelUpAchievementEvent(BUEvent event, Member member) {
        CombatLevelUpAchievementBUEvent e = (CombatLevelUpAchievementBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);

        int level = e.getLevel();
        if (level == 126) {
            builder.append(" has reached the highest possible combat level of 126.");
            return CompletableFuture.completedFuture(builder.build());
        }

        builder.append(" has reached combat level " + level + ".");
        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformCombatTaskAchievementEvent(BUEvent event, Member member) {
        CombatTaskAchievementBUEvent e = (CombatTaskAchievementBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);
        builder.append(" has completed a " + e.getTier() + " combat task: ");
        builder.append(config.chatCombatTaskColor(), e.getName());

        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformQuestCompletionAchievementEvent(BUEvent event, Member member) {
        QuestCompletionAchievementBUEvent e = (QuestCompletionAchievementBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);
        builder.append(" has completed a quest: ");
        builder.append(config.chatQuestNameColor(), e.getName());

        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformDiaryCompletionAchievementEvent(BUEvent event, Member member) {
        DiaryCompletionAchievementBUEvent e = (DiaryCompletionAchievementBUEvent) event;

        String tierText;
        switch (e.getTier()) {
            case Easy:
                tierText = "Easy";
                break;
            case Medium:
                tierText = "Medium";
                break;
            case Hard:
                tierText = "Hard";
                break;
            case Elite:
                tierText = "Elite";
                break;
            default:
                throw new IllegalStateException("Unexpected tier value: " + e.getTier());
        }

        String areaText;
        switch (e.getArea()) {
            case Ardougne:
                areaText = "Ardougne";
                break;
            case Desert:
                areaText = "Desert";
                break;
            case Falador:
                areaText = "Falador";
                break;
            case Kandarin:
                areaText = "Kandarin";
                break;
            case Karamja:
                areaText = "Karamja";
                break;
            case Kourend:
                areaText = "Kourend & Kebos";
                break;
            case Lumbridge:
                areaText = "Lumbridge & Draynor";
                break;
            case Morytania:
                areaText = "Morytania";
                break;
            case Varrock:
                areaText = "Varrock";
                break;
            case Western:
                areaText = "Western Provinces";
                break;
            case Wilderness:
                areaText = "Wilderness";
                break;
            case Fremennik:
                areaText = "Fremennik Province";
                break;
            default:
                throw new IllegalStateException("Unexpected area value: " + e.getArea());
        }

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);
        builder.append(" has completed the " +  tierText + " tier of the ");
        builder.append(config.chatHighlightColor(), areaText);
        builder.append(" diary.");

        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformCollectionLogAchievementEvent(BUEvent event, Member member) {
        CollectionLogUnlockAchievementBUEvent e = (CollectionLogUnlockAchievementBUEvent) event;

        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append("New item added to ");
        builder.append(config.chatPlayerNameColor(), member.getName());
        builder.append("'s collection log: ");
        builder.append(config.chatHighlightColor(), e.getItemName());

        return CompletableFuture.completedFuture(builder.build());
    }

    private CompletableFuture<String> transformValuableLootEvent(BUEvent event, Member member) {
        ValuableLootBUEvent e = (ValuableLootBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);
        builder.append(" has received a drop: ");
        if (e.getQuantity() > 1) {
            builder.append(config.chatHighlightColor(), String.format("%d", e.getQuantity()));
            builder.append(" x ");
        }

        CompletableFuture<String> iconFuture = buChatService.getItemIconTagIfEnabled(e.getItemId());
        return withErrorLogging(iconFuture, "Failed to get item icon tag")
            .thenAccept((itemIconTag) -> {
                if (itemIconTag != null) {
                    builder.append(config.chatHighlightColor(), itemIconTag + " ");
                }
            })
            // Get the item definition on the client thread
            .thenCompose(asyncUtils.onClientThread(() -> client.getItemDefinition(e.getItemId())))
            .thenAccept((itemComposition) -> {
                builder.append(config.chatItemNameColor(), itemComposition.getName());

                int totalCoins = e.getPricePerItem() * e.getQuantity();
                String formattedCoins = String.format("%,d", totalCoins);
                builder.append(" (" + formattedCoins + " coins) from ");
            })
            // Get the NPC definition on the client thread
            .thenCompose(asyncUtils.onClientThread(() -> client.getNpcDefinition(e.getNpcId())))
            .thenApply((npcComposition) -> {
                builder.append(config.chatNPCNameColor(), npcComposition.getName());
                builder.append(".");

                return builder.build();
            });
    }

    private CompletableFuture<String> transformPetDropEvent(BUEvent event, Member member) {
        PetDropBUEvent e = (PetDropBUEvent) event;

        ChatMessageBuilder builder = getChatMessagePlayerNameBuilder(member);

        String followedText = e.isDuplicate()
            ? " has a funny feeling like they would have been followed: "
            : " has a funny feeling like they are being followed: ";
        builder.append(followedText);

        Integer petItemId = e.getPetItemId();
        if (petItemId == null) {
            builder.append(config.chatHighlightColor(), "Unknown");
            return CompletableFuture.completedFuture(builder.build());
        }

        CompletableFuture<String> iconFuture = buChatService.getItemIconTagIfEnabled(petItemId);
        return withErrorLogging(iconFuture, "Failed to get pet icon tag")
            .thenAccept((itemIconTag) -> {
                if (itemIconTag != null) {
                    builder.append(config.chatHighlightColor(), itemIconTag + " ");
                }
            })
            .thenCompose(asyncUtils.onClientThread(() -> client.getItemDefinition(petItemId).getName()))
            .thenApply((petName) -> {
                builder.append(config.chatHighlightColor(), petName);
                return builder.build();
            });
    }
}
