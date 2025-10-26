package com.elertan.policies;

import com.elertan.*;
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
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class TradePolicy extends PolicyBase implements BUPluginLifecycle {
    @Inject
    private Client client;
    @Inject
    private ItemUnlockService itemUnlockService;
    @Inject
    private MemberService memberService;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private BUChatService buChatService;

    private final GameRulesService gameRulesService;

    @Inject
    public TradePolicy(AccountConfigurationService accountConfigurationService, GameRulesService gameRulesService) {
        super(accountConfigurationService, gameRulesService);

        this.gameRulesService = gameRulesService;
    }

    @Override
    public void startUp() throws Exception {

    }

    @Override
    public void shutDown() throws Exception {

    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        MenuAction action = event.getMenuAction();
        if (action.ordinal() >= MenuAction.PLAYER_FIRST_OPTION.ordinal()
                && action.ordinal() <= MenuAction.PLAYER_EIGHTH_OPTION.ordinal()) {
            String option = event.getMenuOption();
            if (option.equalsIgnoreCase("Trade with")) {
                onTradeWithClicked(event);
            }
        }

        Widget widget = event.getWidget();
        if (widget == null) {
            return;
        }

        if (widget.getId() == InterfaceID.Trademain.ACCEPT) {
            onTradeAcceptClicked();
        }
    }

    private void onTradeWithClicked(MenuOptionClicked event) {
        log.info("Trade with clicked");
        if (!shouldEnforcePolicies()) {
            log.info("...but not enforcing policies");
            return;
        }
        GameRules gameRules = gameRulesService.getGameRules();
        if (!gameRules.isPreventTradeOutsideGroup()) {
            log.info("...but not preventing trade outside group");
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

        boolean isPlayingAlone = memberService.isPlayingAlone();
        String message = isPlayingAlone ?
                "You are a Bronzeman. You (sort of) stand alone."
                : "You are a Group Bronzeman. You can only trade members of your group.";
        buChatService.sendMessage(message);

        buSoundHelper.playDisabledSound();
    }

    public void onTradeAcceptClicked() {
//        if (!shouldEnforcePolicies()) {
//            return;
//        }
//        GameRules gameRules = gameRulesService.getGameRules();
//        if (gameRules.isPreventTradeOutsideGroup())
    }
}
