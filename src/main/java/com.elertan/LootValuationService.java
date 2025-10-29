package com.elertan;

import com.elertan.event.ValuableLootBUEvent;
import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

@Slf4j
@Singleton
public class LootValuationService implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private ItemManager itemManager;
    @Inject
    private BUEventService buEventService;
    @Inject
    private GameRulesService gameRulesService;

    @Override
    public void startUp() throws Exception {

    }

    @Override
    public void shutDown() throws Exception {

    }


    public void onServerNpcLoot(ServerNpcLoot event) {
        Collection<ItemStack> itemStacks = event.getItems();
        if (itemStacks == null) {
            return;
        }
        GameRules gameRules = gameRulesService.getGameRules();
        if (gameRules == null) {
            return;
        }
        Integer valuableLootNotificationThreshold = gameRules.getValuableLootNotificationThreshold();
        if (valuableLootNotificationThreshold == null || valuableLootNotificationThreshold <= 0) {
            return;
        }

        for (ItemStack itemStack : itemStacks) {
            int itemId = itemStack.getId();
            int quantity = itemStack.getQuantity();
            int price = itemManager.getItemPrice(itemId);
            int totalPrice = price * quantity;

            if (totalPrice >= valuableLootNotificationThreshold) {
                ValuableLootBUEvent valuableLootBUEvent = new ValuableLootBUEvent(
                    client.getAccountHash(),
                    new ISOOffsetDateTime(OffsetDateTime.now()),
                    itemId,
                    quantity,
                    price
                );
                buEventService.publishEvent(valuableLootBUEvent).whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.error("error publishing valuable loot event", throwable);
                        return;
                    }
                    log.info("published valuable loot event");
                });
            }
        }
    }
}
