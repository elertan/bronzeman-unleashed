package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;

public class PolicyBase {
    private final AccountConfigurationService accountConfigurationService;
    private final GameRulesService gameRulesService;

    public PolicyBase(AccountConfigurationService accountConfigurationService, GameRulesService gameRulesService) {
        this.accountConfigurationService = accountConfigurationService;
        this.gameRulesService = gameRulesService;
    }

    protected boolean shouldEnforcePolicies() {
        try {
            AccountConfiguration accountConfiguration = accountConfigurationService.getCurrentAccountConfiguration();
            if (accountConfiguration == null) {
                return false;
            }
        } catch (Exception ignored) {
            // Can fail when account configuration is not yet initialized
            return false;
        }

        GameRulesService.State gameRulesServiceState = gameRulesService.getState();
        if (gameRulesServiceState != GameRulesService.State.Ready) {
            return false;
        }
        GameRules gameRules = gameRulesService.getGameRules();
        if (gameRules == null) {
            return false;
        }

        return true;
    }
}
