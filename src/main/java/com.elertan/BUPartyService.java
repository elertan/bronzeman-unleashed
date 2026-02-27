package com.elertan;

import com.elertan.models.GameRules;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.party.PartyService;

@Slf4j
public class BUPartyService implements BUPluginLifecycle {
    @Inject private BUPluginConfig buPluginConfig;
    @Inject private GameRulesService gameRulesService;
    @Inject private PartyService partyService;
    @Inject private BUChatService buChatService;
    private boolean isWaitingUntilGameRulesReady = false;

    @Override public void startUp() throws Exception {}
    @Override public void shutDown() throws Exception {}

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGING_IN) return;
        log.debug("Player logging in...");
        if (isWaitingUntilGameRulesReady) {
            log.debug("Already waiting for game rules to be ready");
            return;
        }
        isWaitingUntilGameRulesReady = true;
        gameRulesService.waitUntilGameRulesReady(null).whenComplete((__, throwable) -> {
            isWaitingUntilGameRulesReady = false;
            if (throwable != null) {
                log.error("error waiting for game rules to be ready", throwable);
                return;
            }
            GameRules gameRules = gameRulesService.getGameRules().get();
            String partyPassword = gameRules.getPartyPassword();
            if (partyPassword == null || partyPassword.trim().isEmpty()) {
                log.debug("No party password set");
                return;
            }
            String trimmed = partyPassword.trim();
            if (!buPluginConfig.shouldAutomaticallyJoinPartyOnLogin()) {
                log.debug("Auto-join party disabled");
                buChatService.sendMessage(
                    "The bronzeman game rules configuration has a password set, but the plugin is configured to not automatically join the party on login.");
                return;
            }
            log.debug("Attempting to join party with password {}", trimmed);
            partyService.changeParty(trimmed);
            buChatService.sendMessage(
                "Automatically joined party using bronzeman game rules configuration.");
            log.debug("Joined party with password {}", trimmed);
        });
    }
}
