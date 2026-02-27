package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.WorldTypeService;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PolicyBase implements BUPluginLifecycle {

    protected final AccountConfigurationService accountConfigurationService;
    protected final GameRulesService gameRulesService;
    protected final PolicyService policyService;
    protected final WorldTypeService worldTypeService;

    public PolicyBase(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService,
        WorldTypeService worldTypeService) {
        this.accountConfigurationService = accountConfigurationService;
        this.gameRulesService = gameRulesService;
        this.policyService = policyService;
        this.worldTypeService = worldTypeService;
    }

    @Override
    public void startUp() throws Exception {
    }

    @Override
    public void shutDown() throws Exception {
    }

    @NonNull
    protected PolicyContext createContext() {
        log.debug("creating context from class: {}", this.getClass().getName());

        if (!worldTypeService.isCurrentWorldSupported()) {
            log.debug("Skipping policy - unsupported world type");
            return new PolicyContext(null, false);
        }

        AccountConfiguration accountConfiguration = null;
        try {
            accountConfiguration = accountConfigurationService.getCurrentAccountConfiguration();
        } catch (Exception ignored) {
        }

        if (accountConfiguration == null) {
            return new PolicyContext(null, false);
        }

        GameRules gameRules = gameRulesService.getGameRules().get();
        boolean gameRulesNotLoaded = gameRules == null;

        if (gameRulesNotLoaded) {
            policyService.notifyGameRulesNotLoaded();
        }

        return new PolicyContext(gameRules, gameRulesNotLoaded);
    }

    @Value
    public static class PolicyContext {

        @Nullable GameRules gameRules;
        boolean mustEnforceStrictPolicies;

        public boolean shouldApplyForRules(
            Function<@NonNull GameRules, @NonNull Boolean> rulesApplier) {
            if (mustEnforceStrictPolicies) {
                return true;
            }
            if (gameRules == null) {
                return false;
            }
            return rulesApplier.apply(gameRules);
        }
    }
}
