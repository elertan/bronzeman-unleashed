package com.elertan;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.chat.ChatMessageBuilder;

@Slf4j
@Singleton
public class GameRulesService implements BUPluginLifecycle {

    private static final List<GameRuleDiff> BOOLEAN_RULE_DIFFS = List.of(
        new GameRuleDiff("Only for tradeable items", GameRules::isOnlyForTradeableItems),
        new GameRuleDiff("Restrict ground items", GameRules::isRestrictGroundItems),
        new GameRuleDiff("Prevent Grand Exchange buy offers", GameRules::isPreventGrandExchangeBuyOffers),
        new GameRuleDiff("Prevent Grand Exchange gear buy offers", GameRules::isPreventGrandExchangeGearBuyOffers),
        new GameRuleDiff("Prevent trade locked items", GameRules::isPreventTradeLockedItems),
        new GameRuleDiff("Prevent trade outside group", GameRules::isPreventTradeOutsideGroup),
        new GameRuleDiff("Restrict player owned house", GameRules::isPreventPlayerOwnedHouse),
        new GameRuleDiff("Restrict PvP loot", GameRules::isRestrictPlayerVersusPlayerLoot),
        new GameRuleDiff("Restrict Falador Party Room balloons", GameRules::isRestrictFaladorPartyRoomBalloons),
        new GameRuleDiff("Share achievement notifications", GameRules::isShareAchievementNotifications)
    );

    @Getter
    private final Observable<GameRules> gameRules = Observable.empty();
    @Inject
    private GameRulesDataProvider gameRulesDataProvider;
    @Inject
    private BUChatService buChatService;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private MemberService memberService;
    private Subscription gameRulesSubscription;

    @Override
    public void startUp() throws Exception {
        gameRulesSubscription = gameRulesDataProvider.getGameRules().subscribe(this::onGameRulesChanged);
    }

    @Override
    public void shutDown() throws Exception {
        if (gameRulesSubscription != null) {
            gameRulesSubscription.dispose();
            gameRulesSubscription = null;
        }
        gameRules.clear();
    }

    /**
     * Wait until game rules are ready.
     *
     * @param timeout timeout duration
     * @return future that completes when game rules are ready
     */
    public CompletableFuture<GameRules> waitUntilGameRulesReady(Duration timeout) {
        return gameRules.await(timeout);
    }

    private void onGameRulesChanged(GameRules newGameRules, GameRules oldGameRules) {
        if (newGameRules != null && oldGameRules != null) {
            {
                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append("Game rules have been updated");

                if (newGameRules.getLastUpdatedByAccountHash() != null) {
                    Member member = null;
                    try {
                        member = memberService.getMemberByAccountHash(newGameRules.getLastUpdatedByAccountHash());
                    } catch (Exception e) {
                        // ignored
                    }
                    if (member != null) {
                        builder.append(" by ");
                        builder.append(buPluginConfig.chatPlayerNameColor(), member.getName());
                    }
                }
                builder.append(".");

                buChatService.sendMessage(builder.build());
            }

            Map<String, String> differences = generateGameRulesUpdateDifference(
                oldGameRules,
                newGameRules
            );
            if (differences != null && !differences.isEmpty()) {
                for (Map.Entry<String, String> entry : differences.entrySet()) {
                    ChatMessageBuilder builder = new ChatMessageBuilder();
                    builder.append(" - ");
                    builder.append(entry.getKey());
                    builder.append(": ");
                    builder.append(buPluginConfig.chatHighlightColor(), entry.getValue());

                    buChatService.sendMessage(builder.build());
                }
            }
        }

        gameRules.set(newGameRules);
    }

    private Map<String, String> generateGameRulesUpdateDifference(GameRules oldGameRules,
        GameRules newGameRules) {
        if (oldGameRules == null || newGameRules == null) {
            return null;
        }

        Map<String, String> differences = new HashMap<>();

        for (GameRuleDiff diff : BOOLEAN_RULE_DIFFS) {
            boolean oldVal = diff.getter.apply(oldGameRules);
            boolean newVal = diff.getter.apply(newGameRules);
            if (oldVal != newVal) {
                differences.put(diff.label, newVal ? "enabled" : "disabled");
            }
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

        if (!Objects.equals(oldGameRules.getPartyPassword(), newGameRules.getPartyPassword())) {
            differences.put("Party password", "*hidden see config*");
        }

        return differences;
    }

    private static class GameRuleDiff {

        final String label;
        final Function<GameRules, Boolean> getter;

        GameRuleDiff(String label, Function<GameRules, Boolean> getter) {
            this.label = label;
            this.getter = getter;
        }
    }
}
