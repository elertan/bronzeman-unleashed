package com.elertan;

import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;

@Singleton
public class PolicyService implements BUPluginLifecycle {

    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;

    @Getter
    private boolean hasNotifiedGameRulesNotLoaded = false;

    @Override
    public void startUp() throws Exception {

    }

    @Override
    public void shutDown() throws Exception {

    }

    public void notifyGameRulesNotLoaded() {
        if (hasNotifiedGameRulesNotLoaded) {
            return;
        }
        hasNotifiedGameRulesNotLoaded = true;

        buChatService.sendErrorMessage(chatMessageProvider.messageFor(
            MessageKey.STILL_LOADING_TEMPORARY_STRICT_GAME_RULES_ENFORCEMENT));
    }
}
