package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.elertan.models.GameRules;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;

@Slf4j
@Singleton
public class PlayerOwnedHousePolicy extends PolicyBase implements BUPluginLifecycle {

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
        MenuAction menuAction = event.getMenuAction();
        String menuOption = event.getMenuOption();
        Widget widget = event.getWidget();

        log.info(
            "menu action: {}, menu option: {}, widget: {}",
            menuAction, menuOption, widget
        );

        if (!menuOption.equals("Enter house")) {
            log.info("is enter house");
            return;
        }
    }
}
