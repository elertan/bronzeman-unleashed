package com.elertan;

import com.elertan.models.GameRules;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.party.PartyService;

@Slf4j
public class BUPartyService implements BUPluginLifecycle {

    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private GameRulesService gameRulesService;
    @Inject
    private PartyService partyService;
    @Inject
    private BUChatService buChatService;

    private boolean isWaitingUntilGameRulesReady = false;

    @Override
    public void startUp() throws Exception {
    }

    @Override
    public void shutDown() throws Exception {
    }

    public void onGameStateChanged(GameStateChanged event) {
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGGING_IN) {
            log.info("Player logging in...");

            if (isWaitingUntilGameRulesReady) {
                log.info("Already waiting for game rules to be ready, not waiting again");
                return;
            }

            isWaitingUntilGameRulesReady = true;
            gameRulesService.waitUntilGameRulesReady(null)
                .whenComplete((__, throwable) -> {
                    isWaitingUntilGameRulesReady = false;

                    if (throwable != null) {
                        log.error("error waiting for game rules to be ready", throwable);
                        return;
                    }

                    log.debug(
                        "Waited after login for game rules to be ready, attempting to join party if configured");

                    GameRules gameRules = gameRulesService.getGameRules();
                    String partyPassword = gameRules.getPartyPassword();
                    if (partyPassword == null || partyPassword.isEmpty()) {
                        log.debug("No party password set, not attempting to join party");
                        return;
                    }
                    String trimmedPartyPassword = partyPassword.trim();
                    if (trimmedPartyPassword.isEmpty()) {
                        log.debug("Party password is empty, not attempting to join party");
                        return;
                    }

                    if (!buPluginConfig.shouldAutomaticallyJoinPartyOnLogin()) {
                        log.debug(
                            "Plugin is not configured to automatically join the party on login");

                        ChatMessageBuilder builder = new ChatMessageBuilder();
                        builder.append(
                            "The bronzeman game rules configuration has a password set, but the plugin is configured to not automatically join the party on login.");
                        buChatService.sendMessage(builder.build());
                        return;
                    }

                    log.debug("Attempting to join party with password {}", trimmedPartyPassword);
                    partyService.changeParty(trimmedPartyPassword);

                    ChatMessageBuilder builder = new ChatMessageBuilder();
                    builder.append(
                        "Automatically joined party using bronzeman game rules configuration.");
                    buChatService.sendMessage(builder.build());

                    log.debug("Joined party with password {}", trimmedPartyPassword);
                });
        }
    }
}
