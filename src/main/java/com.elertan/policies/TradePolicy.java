package com.elertan.policies;

import static com.elertan.chat.ChatMessageProvider.MessageKey.TRADE_RESTRICTION;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.GameRulesService;
import com.elertan.MemberService;
import com.elertan.PolicyService;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.utils.TextUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;

@Slf4j
@Singleton
public class TradePolicy extends PolicyBase {

    @Inject private Client client;
    @Inject private MemberService memberService;
    @Inject private BUChatService buChatService;

    @Inject
    public TradePolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        MenuAction action = event.getMenuAction();
        String menuOption = event.getMenuOption();
        if (action.ordinal() >= MenuAction.PLAYER_FIRST_OPTION.ordinal()
            && action.ordinal() <= MenuAction.PLAYER_EIGHTH_OPTION.ordinal()
            && menuOption.equalsIgnoreCase("Trade with")) {
            onTradeWithClicked(event);
        }
        if (menuOption.equalsIgnoreCase("Accept trade")) {
            onChatAcceptTradeClicked(event);
        }
    }

    private void onChatAcceptTradeClicked(MenuOptionClicked event) {
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventTradeLockedItems)) return;
        String name = TextUtils.sanitizePlayerName(event.getMenuTarget());
        if (memberService.getMemberByName(name) != null) return;
        event.consume();
        buChatService.sendRestrictionMessage(TRADE_RESTRICTION);
    }

    private void onTradeWithClicked(MenuOptionClicked event) {
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventTradeOutsideGroup)) return;
        String target = TextUtils.sanitizePlayerName(event.getMenuTarget());
        log.info("Player is trying to trade with '{}'...", target);
        Member member = memberService.getMemberByName(target);
        if (member != null) { log.info("...and is a member of our group. All good!"); return; }
        log.info("...and is not a member of our group.");
        event.consume();
        buChatService.sendRestrictionMessage(TRADE_RESTRICTION);
    }
}
