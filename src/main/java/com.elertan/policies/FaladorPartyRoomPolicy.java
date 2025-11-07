package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.models.GameRules;
import com.google.inject.Singleton;
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
        if (gameRules == null || !gameRules.isRestrictGroundItems()) {
            return;
        }
        enforceMenuOptionClicked(event, context);
    }

    private void enforceMenuOptionClicked(MenuOptionClicked event, PolicyContext context) {
        String menuOption = event.getMenuOption();
    }
}
