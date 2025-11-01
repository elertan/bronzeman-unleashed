package com.elertan;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.utils.ListenerUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.chat.ChatMessageBuilder;

@Slf4j
@Singleton
public class GameRulesService implements BUPluginLifecycle {

    private final ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();
    @Inject
    private GameRulesDataProvider gameRulesDataProvider;
    @Inject
    private BUChatService buChatService;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private MemberService memberService;
    @Getter
    private State state = State.NotReady;
    @Getter
    private GameRules gameRules;
    private final Consumer<GameRules> gameRulesListener = this::gameRulesListener;

    @Override
    public void startUp() throws Exception {
        gameRulesDataProvider.addGameRulesListener(gameRulesListener);
    }

    @Override
    public void shutDown() throws Exception {
        gameRulesDataProvider.removeGameRulesListener(gameRulesListener);

        gameRules = null;
        state = State.NotReady;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public CompletableFuture<Void> waitUntilGameRulesReady(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            private Consumer<GameRules> listener;

            @Override
            public boolean isReady() {
                return gameRules != null;
            }

            @Override
            public void addListener(Runnable notify) {
                listener = gameRules -> notify.run();
                gameRulesDataProvider.addGameRulesListener(listener);
            }

            @Override
            public void removeListener() {
                if (listener == null) {
                    return;
                }
                gameRulesDataProvider.removeGameRulesListener(listener);
                listener = null;
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    private void gameRulesListener(GameRules gameRules) {
        GameRules oldGameRules = this.gameRules;
        this.gameRules = gameRules;
        if (gameRules == null) {
            setState(State.NotReady);
        } else {
            setState(State.Ready);
        }

        if (gameRules != null && oldGameRules != null) {
            {
                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append("Game rules have been updated");

                if (gameRules.getLastUpdatedByAccountHash() != null) {
                    Member member = null;
                    try {
                        member = memberService.getMemberByAccountHash(gameRules.getLastUpdatedByAccountHash());
                    } catch (Exception e) {
                        // ignored
                    }
                    if (member != null) {
                        builder.append(" by ");
                        builder.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                    }
                }
//                if (gameRules.getLastUpdatedAt() != null) {
//                    builder.append(" at ");
//                    ISOOffsetDateTime lastUpdatedAt = gameRules.getLastUpdatedAt();
//                    String formattedMoment = lastUpdatedAt.getValue()
//                        .format(DateTimeFormatter.ISO_LOCAL_TIME);
//                    builder.append(buPluginConfig.chatHighlightColor(), formattedMoment);
//                }
                builder.append(".");

                buChatService.sendMessage(builder.build());
            }

            Map<String, String> differences = generateGameRulesUpdateDifference(
                oldGameRules,
                gameRules
            );
            if (differences != null && !differences.isEmpty()) {
                for (Map.Entry<String, String> entry : differences.entrySet()) {
                    ChatMessageBuilder builder = new ChatMessageBuilder();
                    builder.append("  > ");
                    builder.append(entry.getKey());
                    builder.append(": ");
                    builder.append(buPluginConfig.chatHighlightColor(), entry.getValue());

                    buChatService.sendMessage(builder.build());
                }
            }

        }

        for (Listener listener : listeners) {
            try {
                listener.onGameRulesUpdate(gameRules, oldGameRules);
            } catch (Exception e) {
                log.error("Error while notifying listener on GameRulesService.", e);
            }
        }
    }

    private Map<String, String> generateGameRulesUpdateDifference(GameRules oldGameRules,
        GameRules newGameRules) {
        if (oldGameRules == null || newGameRules == null) {
            return null;
        }

        Function<Boolean, String> booleanFormatter = (value) -> value ? "enabled" : "disabled";

        Map<String, String> differences = new HashMap<>();
        if (oldGameRules.isOnlyForTradeableItems() != newGameRules.isOnlyForTradeableItems()) {
            differences.put(
                "Only for tradeable items",
                booleanFormatter.apply(newGameRules.isOnlyForTradeableItems())
            );
        }
        if (oldGameRules.isRestrictGroundItems() != newGameRules.isRestrictGroundItems()) {
            differences.put(
                "Restrict ground items",
                booleanFormatter.apply(newGameRules.isRestrictGroundItems())
            );
        }
        if (oldGameRules.isPreventGrandExchangeBuyOffers()
            != newGameRules.isPreventGrandExchangeBuyOffers()) {
            differences.put(
                "Prevent Grand Exchange buy offers",
                booleanFormatter.apply(newGameRules.isPreventGrandExchangeBuyOffers())
            );
        }
        if (oldGameRules.isPreventTradeLockedItems() != newGameRules.isPreventTradeLockedItems()) {
            differences.put(
                "Prevent trade locked items",
                booleanFormatter.apply(newGameRules.isPreventTradeLockedItems())
            );
        }
        if (oldGameRules.isPreventTradeOutsideGroup()
            != newGameRules.isPreventTradeOutsideGroup()) {
            differences.put(
                "Prevent trade outside group",
                booleanFormatter.apply(newGameRules.isPreventTradeOutsideGroup())
            );
        }
        if (oldGameRules.isPreventPlayedOwnedHouse() != newGameRules.isPreventPlayedOwnedHouse()) {
            differences.put(
                "Restrict played owned house",
                booleanFormatter.apply(newGameRules.isPreventPlayedOwnedHouse())
            );
        }
        if (oldGameRules.isRestrictPlayerVersusPlayerLoot()
            != newGameRules.isRestrictPlayerVersusPlayerLoot()) {
            differences.put(
                "Restrict PvP loot",
                booleanFormatter.apply(newGameRules.isRestrictPlayerVersusPlayerLoot())
            );
        }
        if (!Objects.equals(
            oldGameRules.getValuableLootNotificationThreshold(),
            newGameRules.getValuableLootNotificationThreshold()
        )) {
            Integer threshold = newGameRules.getValuableLootNotificationThreshold();
            String newValue;
            if (threshold == null || threshold <= 0) {
                newValue = "disabled";
            } else {
                newValue = String.format("%,d", threshold) + " coins";
            }
            differences.put("Valuable loot notification threshold", newValue);
        }
        if (oldGameRules.isShareAchievementNotifications()
            != newGameRules.isShareAchievementNotifications()) {
            differences.put(
                "Share achievement notifications",
                booleanFormatter.apply(newGameRules.isShareAchievementNotifications())
            );
        }
        if (!Objects.equals(oldGameRules.getPartyPassword(), newGameRules.getPartyPassword())) {
            differences.put("Party password", "*hidden see config*");
        }
        return differences;
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
    }

    public enum State {
        NotReady, Ready,
    }

    public interface Listener {

        void onGameRulesUpdate(GameRules newGameRules, GameRules oldGameRules);
    }
}
