package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.models.GameRules;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class FaladorPartyRoomPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;

    @Inject
    public FaladorPartyRoomPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {

    }

    @Override
    public void shutDown() throws Exception {

    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isRestrictFaladorPartyRoomBalloons)) {
            return;
        }

        String menuOption = event.getMenuOption();
        if (!menuOption.equalsIgnoreCase("burst")) {
            return;
        }
        String menuTarget = event.getMenuTarget();
        String sanitizedMenuTarget = Text.sanitize(Text.removeTags(menuTarget));
        if (!sanitizedMenuTarget.equalsIgnoreCase("party balloon")) {
            return;
        }

        event.consume();
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(
            buPluginConfig.chatRestrictionColor(),
            chatMessageProvider.messageFor(MessageKey.FALADOR_PARTY_ROOM_BALLOON_RESTRICTION)
        );
        buChatService.sendMessage(builder.build());
    }
}
