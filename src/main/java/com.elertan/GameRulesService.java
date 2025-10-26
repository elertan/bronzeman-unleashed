package com.elertan;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.Member;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.chat.ChatMessageBuilder;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class GameRulesService implements BUPluginLifecycle {
    public enum State {
        NotReady,
        Ready,
    }

    public interface Listener {
        void onGameRulesUpdate(GameRules newGameRules, GameRules oldGameRules);
    }

    @Inject
    private GameRulesDataProvider gameRulesDataProvider;
    @Inject
    private BUChatService buChatService;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private MemberService memberService;

    private final ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();
    private final Consumer<GameRules> gameRulesListener = this::gameRulesListener;

    @Getter
    private State state = State.NotReady;

    @Getter
    private GameRules gameRules;

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

    private void gameRulesListener(GameRules gameRules) {
        GameRules oldGameRules = this.gameRules;
        this.gameRules = gameRules;
        if (gameRules == null) {
            setState(State.NotReady);
        } else {
            setState(State.Ready);
        }

        if (gameRules != null && oldGameRules != null) {
            ChatMessageBuilder builder = new ChatMessageBuilder();
            builder.append("Game rules has been updated");

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
            if (gameRules.getLastUpdatedAt() != null) {
                builder.append(" at ");
                ISOOffsetDateTime lastUpdatedAt = gameRules.getLastUpdatedAt();
                String formattedMoment = lastUpdatedAt.getValue().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
                builder.append(buPluginConfig.chatHighlightColor(), formattedMoment);
            }
            builder.append(".");

            buChatService.sendMessage(builder.build());
        }

        for (Listener listener : listeners) {
            try {
                listener.onGameRulesUpdate(gameRules, oldGameRules);
            } catch (Exception e) {
                log.error("Error while notifying listener on GameRulesService.", e);
            }
        }
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
    }
}
