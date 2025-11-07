package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.models.GameRules;
import com.google.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;

@Singleton
public class FaladorPartyRoomPolicy extends PolicyBase implements BUPluginLifecycle {

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
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        PolicyContext context = createContext();
        if (context.isMustEnforceStrictPolicies()) {
            enforceMenuOptionClicked(event, context);
            return;
        }
        GameRules gameRules = context.getGameRules();
        if (gameRules == null || !gameRules.isRestrictFaladorPartyRoomBalloons()) {
            return;
        }
        enforceMenuOptionClicked(event, context);
    }

    private void enforceMenuOptionClicked(MenuOptionClicked event, PolicyContext context) {
        MenuEntry menuEntry = event.getMenuEntry();
        if (menuEntry != null) {
            log.info("menu entry: {}", menuEntry.getIdentifier());
            Actor actor = menuEntry.getActor();
            if (actor != null) {
                log.info("actor name: {}", actor.getName());
            }
        }

        log.info(
            "mo: {}, mao: {}, id: {}, target: {}",
            event.getMenuOption(),
            event.getMenuAction().ordinal(),
            event.getId(),
            event.getMenuTarget()
        );

        String menuOption = event.getMenuOption();
        if (!menuOption.equalsIgnoreCase("burst")) {
            return;
        }
    }
}
