package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUSoundHelper;
import com.elertan.GameRulesService;
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
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

@Slf4j
@Singleton
public class PlayerOwnedHousePolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private MemberService memberService;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private BUSoundHelper buSoundHelper;

    @Inject
    public PlayerOwnedHousePolicy(AccountConfigurationService accountConfigurationService,
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
        PolicyContext context = createContext();
        if (context.isMustEnforceStrictPolicies()) {
            enforcePolicyMenuOptionClicked(event);
            return;
        }
        GameRules gameRules = context.getGameRules();
        if (gameRules == null || !gameRules.isPreventPlayedOwnedHouse()) {
            return;
        }
        enforcePolicyMenuOptionClicked(event);
    }

    private void enforcePolicyMenuOptionClicked(MenuOptionClicked event) {
        String menuOption = event.getMenuOption();
        if (!menuOption.equalsIgnoreCase("enter house")) {
            return;
        }

        Widget buttonWidget = event.getWidget();
        if (buttonWidget == null) {
            log.error("Button widget is null played owned house policy");
            return;
        }
        Widget pohboardNameWidget = client.getWidget(InterfaceID.PohBoard.NAME);
        if (pohboardNameWidget == null) {
            log.error("Pohboard name widget is null played owned house policy");
            return;
        }
        int buttonWidgetIdx = buttonWidget.getIndex();
        Widget pohNameWidget = pohboardNameWidget.getChild(buttonWidgetIdx);
        if (pohNameWidget == null) {
            log.error("Poh name widget is null played owned house policy");
            return;
        }
        String pohNameWidgetText = pohNameWidget.getText();
        if (pohNameWidgetText == null) {
            log.error("Poh name widget text is null played owned house policy");
            return;
        }
        String playerName = TextUtils.sanitizePlayerName(pohNameWidgetText);
        Member member;
        try {
            member = memberService.getMemberByName(playerName);
        } catch (Exception ex) {
            buChatService.sendMessage(chatMessageProvider.messageFor(MessageKey.STILL_LOADING_PLEASE_WAIT));
            event.consume();
            buSoundHelper.playDisabledSound();
            return;
        }

        if (member != null) {
            return;
        }

        buChatService.sendMessage(chatMessageProvider.messageFor(MessageKey.POH_ENTER_RESTRICTION));
        event.consume();
        buSoundHelper.playDisabledSound();
    }
}
