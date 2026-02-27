package com.elertan;

import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;

@Singleton
public class PolicyService {
    @Inject private BUChatService buChatService;
    @Inject private ChatMessageProvider chatMessageProvider;
    @Getter private boolean hasNotifiedGameRulesNotLoaded = false;

    public void notifyGameRulesNotLoaded() {
        if (hasNotifiedGameRulesNotLoaded) return;
        hasNotifiedGameRulesNotLoaded = true;
        buChatService.sendErrorMessage(chatMessageProvider.messageFor(
            MessageKey.STILL_LOADING_TEMPORARY_STRICT_GAME_RULES_ENFORCEMENT));
    }
}
