package com.elertan;

import com.elertan.models.GameRules;
import com.google.inject.Inject;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.party.PartyService;

public class BUPartyService implements BUPluginLifecycle {
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private GameRulesService gameRulesService;
    @Inject
    private PartyService partyService;
    @Inject
    private BUChatService buChatService;

    private GameRulesService.Listener gameRulesListener;

    private boolean shouldTryToJoinParty = false;

    @Override
    public void startUp() throws Exception {
        gameRulesListener = new GameRulesService.Listener() {
            @Override
            public void onGameRulesUpdate(GameRules newGameRules, GameRules oldGameRules) {
                if (newGameRules == null) {
                    return;
                }
                if (!shouldTryToJoinParty) {
                    return;
                }
                shouldTryToJoinParty = false;

                String partyPassword = newGameRules.getPartyPassword();
                if (partyPassword == null || partyPassword.isEmpty()) {
                    return;
                }
                String trimmedPartyPassword = partyPassword.trim();
                if (trimmedPartyPassword.isEmpty()) {
                    return;
                }

                if (!buPluginConfig.shouldAutomaticallyJoinPartyOnLogin()) {
                    ChatMessageBuilder builder = new ChatMessageBuilder();
                    builder.append("The bronzeman game rules configuration has a password set, but the plugin is configured to not automatically join the party on login.");
                    buChatService.sendMessage(builder.build());
                    return;
                }

                partyService.changeParty(trimmedPartyPassword);

                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append("Automatically joined party using bronzeman game rules configuration.");
                buChatService.sendMessage(builder.build());
            }
        };

        gameRulesService.addListener(gameRulesListener);
    }

    @Override
    public void shutDown() throws Exception {
        gameRulesService.removeListener(gameRulesListener);
    }

    public void onGameStateChanged(GameStateChanged event) {
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGGING_IN) {
            shouldTryToJoinParty = true;
        }
    }
}
