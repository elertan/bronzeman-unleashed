package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PolicyBase {

    protected final AccountConfigurationService accountConfigurationService;
    protected final GameRulesService gameRulesService;
    protected final PolicyService policyService;

    public PolicyBase(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        this.accountConfigurationService = accountConfigurationService;
        this.gameRulesService = gameRulesService;
        this.policyService = policyService;
    }

    @NonNull
    protected PolicyContext createContext() {
        log.debug("creating context from class: {}", this.getClass().getName());
        AccountConfiguration accountConfiguration = null;
        try {
            accountConfiguration = accountConfigurationService.getCurrentAccountConfiguration();
        } catch (Exception ignored) {
        }

        if (accountConfiguration == null) {
            return new PolicyContext(null, false);
        }

        GameRules gameRules = gameRulesService.getGameRules();
        boolean gameRulesNotLoaded = gameRules == null;

        if (gameRulesNotLoaded) {
            policyService.notifyGameRulesNotLoaded();
        }

        return new PolicyContext(gameRules, gameRulesNotLoaded);
    }

    @AllArgsConstructor
    public static class PolicyContext {

        @Getter
        @Nullable
        private final GameRules gameRules;
        @Getter
        private final boolean mustEnforceStrictPolicies;
    }
}
