package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUSoundHelper;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
import com.elertan.MemberService;
import com.elertan.PolicyService;
import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.utils.TextUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageBuilder;

@Slf4j
@Singleton
public class TradePolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private ItemUnlockService itemUnlockService;
    @Inject
    private MemberService memberService;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;

    @Inject
    public TradePolicy(AccountConfigurationService accountConfigurationService,
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
        MenuAction action = event.getMenuAction();
        String menuOption = event.getMenuOption();

        if (action.ordinal() >= MenuAction.PLAYER_FIRST_OPTION.ordinal()
            && action.ordinal() <= MenuAction.PLAYER_EIGHTH_OPTION.ordinal()) {
            if (menuOption.equalsIgnoreCase("Trade with")) {
                onTradeWithClicked(event);
            }
        }

        if (menuOption.equalsIgnoreCase("Accept trade")) {
            onChatAcceptTradeClicked(event);
        }

        Widget widget = event.getWidget();
        if (widget == null) {
            return;
        }

        if (widget.getId() == InterfaceID.Trademain.ACCEPT) {
            onTradeWindowAcceptClicked();
        }
    }

    private void onChatAcceptTradeClicked(MenuOptionClicked event) {
        PolicyContext context = createContext();
        GameRules gameRules = context.getGameRules();
        boolean enforcePolicy =
            context.isMustEnforceStrictPolicies() || (gameRules != null
                && gameRules.isPreventTradeOutsideGroup());

        if (!enforcePolicy) {
            return;
        }

        String menuTarget = event.getMenuTarget();
        String name = TextUtils.sanitizePlayerName(menuTarget);
        Member member = memberService.getMemberByName(name);
        if (member != null) {
            return;
        }

        event.consume();
        tradeRestrictionError();
    }

    private void onTradeWithClicked(MenuOptionClicked event) {
        PolicyContext context = createContext();
        GameRules gameRules = context.getGameRules();
        boolean enforcePolicy =
            context.isMustEnforceStrictPolicies() || (gameRules != null
                && gameRules.isPreventTradeOutsideGroup());

        if (!enforcePolicy) {
            log.info("...but not enforcing policies");
            return;
        }

        String menuTarget = event.getMenuTarget();
        String sanitizedTargetName = TextUtils.sanitizePlayerName(menuTarget);

        log.info("Player is trying to trade with '{}'...", sanitizedTargetName);
        Member member = memberService.getMemberByName(sanitizedTargetName);
        if (member != null) {
            log.info("...and is a member of our group. All good!");
            return;
        }
        log.info("...and is not a member of are group.");

        event.consume();
        tradeRestrictionError();
    }

    public void onTradeWindowAcceptClicked() {
//        PolicyContext context = createContext();
//        GameRules gameRules = context.getGameRules();
//        boolean enforcePolicy =
//            context.isMustEnforceStrictPolicies() || (gameRules != null
//                && gameRules.isPreventTradeLockedItems());
//
//        if (!enforcePolicy) {
//            return;
//        }
    }

    private void tradeRestrictionError() {
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(
            buPluginConfig.chatRestrictionColor(),
            chatMessageProvider.messageFor(MessageKey.TRADE_RESTRICTION)
        );
        buChatService.sendMessage(builder.build());
        buSoundHelper.playDisabledSound();
    }
}
