package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.TileItem;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;

@Slf4j
public class GroundItemsPolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;

    @Inject
    public GroundItemsPolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService) {
        super(accountConfigurationService, gameRulesService);
    }

    @Override
    public void startUp() throws Exception {

    }

    @Override
    public void shutDown() throws Exception {

    }

    public void onItemSpawned(ItemSpawned event) {
        if (!shouldEnforcePolicies()) {
            return;
        }

        TileItem tileItem = event.getItem();
    }

    public void onItemDespawned(ItemDespawned event) {
        if (!shouldEnforcePolicies()) {
            return;
        }

        TileItem tileItem = event.getItem();
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!shouldEnforcePolicies()) {
            return;
        }

        MenuAction menuAction = event.getMenuAction();
        boolean isGroundItemMenuAction =
            menuAction.ordinal() >= MenuAction.GROUND_ITEM_FIRST_OPTION.ordinal()
                && menuAction.ordinal() <= MenuAction.GROUND_ITEM_FIFTH_OPTION.ordinal();
        if (!isGroundItemMenuAction) {
            return;
        }

        String menuOption = event.getMenuOption();
        if (!menuOption.equals("Take")) {
            return;
        }
        int itemId = event.getId();
        if (itemId <= 1) {
            return;
        }

        ItemComposition itemComposition = client.getItemDefinition(itemId);
        log.info("Taking '{}'", itemComposition.getName());
    }
}
