package com.elertan;

import com.elertan.event.BUEvent.ValuableLootBUEvent;
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
    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private BUEventService buEventService;
    @Inject private GameRulesService gameRulesService;
    @Inject private AccountConfigurationService accountConfigurationService;

    @Override public void startUp() throws Exception {}
    @Override public void shutDown() throws Exception {}

    public void onServerNpcLoot(ServerNpcLoot event) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) return;
        Collection<ItemStack> items = event.getItems();
        if (items == null) return;
        GameRules gameRules = gameRulesService.getGameRules().get();
        if (gameRules == null) return;
        Integer threshold = gameRules.getValuableLootNotificationThreshold();
        if (threshold == null || threshold <= 0) return;
        int npcId = event.getComposition().getId();

        for (ItemStack stack : items) {
            int price = itemManager.getItemPrice(stack.getId());
            int totalPrice = price * stack.getQuantity();
            if (totalPrice >= threshold) {
                buEventService.publishEvent(new ValuableLootBUEvent(
                    client.getAccountHash(), new ISOOffsetDateTime(OffsetDateTime.now()),
                    stack.getId(), stack.getQuantity(), price, npcId
                )).whenComplete((__, throwable) -> {
                    if (throwable != null) log.error("error publishing valuable loot event", throwable);
                    else log.info("published valuable loot event");
                });
            }
        }
    }
}
