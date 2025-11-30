package com.elertan;

import com.elertan.chat.ChatMessageEventBroadcaster;
import com.elertan.data.GameRulesDataProvider;
import com.elertan.data.GroundItemOwnedByDataProvider;
import com.elertan.data.LastEventDataProvider;
import com.elertan.data.MembersDataProvider;
import com.elertan.data.UnlockedItemsDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.policies.FaladorPartyRoomPolicy;
import com.elertan.policies.GrandExchangePolicy;
import com.elertan.policies.GroundItemsPolicy;
import com.elertan.policies.PlayerOwnedHousePolicy;
import com.elertan.policies.PlayerVersusPlayerPolicy;
import com.elertan.policies.ShopPolicy;
import com.elertan.policies.TradePolicy;
import com.elertan.remote.RemoteStorageService;
import com.google.inject.Inject;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.AccountHashChanged;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
    name = "Bronzeman Unleashed (BETA)",
    description = "Redefine your adventure - craft your own rules, earn every unlock, and share the grind with friends.",
    tags = {
        "bronzeman",
        "gamemode",
        "restrict",
        "trade",
        "group"
    }
)
public final class BUPlugin extends Plugin {

    @Inject
    private BUResourceService buResourceService;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private RemoteStorageService remoteStorageService;
    @Inject
    private MembersDataProvider membersDataProvider;
    @Inject
    private GameRulesDataProvider gameRulesDataProvider;
    @Inject
    private UnlockedItemsDataProvider unlockedItemsDataProvider;
    @Inject
    private LastEventDataProvider lastEventDataProvider;
    @Inject
    private GroundItemOwnedByDataProvider groundItemOwnedByDataProvider;
    @Inject
    private BUPanelService buPanelService;
    @Inject
    private BUOverlayService buOverlayService;
    @Inject
    private BUChatService buChatService;
    @Inject
    private MemberService memberService;
    @Inject
    private GameRulesService gameRulesService;
    @Inject
    private BUPartyService buPartyService;
    @Inject
    private BUEventService buEventService;
    @Inject
    private ItemUnlockService itemUnlockService;
    @Inject
    private PolicyService policyService;
    @Inject
    private AchievementDiaryService achievementDiaryService;
    @Inject
    private GrandExchangePolicy grandExchangePolicy;
    @Inject
    private TradePolicy tradePolicy;
    @Inject
    private ShopPolicy shopPolicy;
    @Inject
    private GroundItemsPolicy groundItemsPolicy;
    @Inject
    private ChatMessageEventBroadcaster chatMessageEventBroadcaster;
    @Inject
    private LootValuationService lootValuationService;
    @Inject
    private PlayerOwnedHousePolicy playerOwnedHousePolicy;
    @Inject
    private PlayerVersusPlayerPolicy playerVersusPlayerPolicy;
    @Inject
    private FaladorPartyRoomPolicy faladorPartyRoomPolicy;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private BUPluginConfig config;

    private boolean started;
    private List<BUPluginLifecycle> lifecycleDependencies;
    private final Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;

    @Inject
    private void initLifecycleDependencies() {
        if (lifecycleDependencies != null) {
            return;
        }
        log.debug("Initializing lifecycle dependencies");
        lifecycleDependencies = new ArrayList<>();

        // Core
        lifecycleDependencies.add(buResourceService);
        lifecycleDependencies.add(accountConfigurationService);
        lifecycleDependencies.add(remoteStorageService);
        // Data providers
        lifecycleDependencies.add(membersDataProvider);
        lifecycleDependencies.add(gameRulesDataProvider);
        lifecycleDependencies.add(unlockedItemsDataProvider);
        lifecycleDependencies.add(lastEventDataProvider);
        lifecycleDependencies.add(groundItemOwnedByDataProvider);
        // Services
        lifecycleDependencies.add(buPanelService);
        lifecycleDependencies.add(buOverlayService);
        lifecycleDependencies.add(buChatService);
        lifecycleDependencies.add(memberService);
        lifecycleDependencies.add(gameRulesService);
        lifecycleDependencies.add(itemUnlockService);
        lifecycleDependencies.add(buPartyService);
        lifecycleDependencies.add(buEventService);
        lifecycleDependencies.add(lootValuationService);
        lifecycleDependencies.add(policyService);
        lifecycleDependencies.add(achievementDiaryService);
        // Policies
        lifecycleDependencies.add(grandExchangePolicy);
        lifecycleDependencies.add(tradePolicy);
        lifecycleDependencies.add(shopPolicy);
        lifecycleDependencies.add(groundItemsPolicy);
        lifecycleDependencies.add(playerOwnedHousePolicy);
        lifecycleDependencies.add(playerVersusPlayerPolicy);
        lifecycleDependencies.add(faladorPartyRoomPolicy);

        lifecycleDependencies.add(chatMessageEventBroadcaster);
    }

    @Provides
    BUPluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BUPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.debug("BU: startup begin");
        super.startUp();
        initLifecycleDependencies();

        try {
            accountConfigurationService.addCurrentAccountConfigurationChangeListener(
                currentAccountConfigurationChangeListener);

            for (BUPluginLifecycle lifecycleDependency : lifecycleDependencies) {
                lifecycleDependency.startUp();
            }

            started = true;
            log.debug("BU: startup ok");
        } catch (Exception e) {
            started = false;
            log.error("BU: startup failed", e);
            throw e;
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log.debug("BU: shutdown begin");
        Exception failure = null;
        try {
            if (started) {
                for (int i = lifecycleDependencies.size() - 1; i >= 0; i--) {
                    lifecycleDependencies.get(i).shutDown();
                }

                accountConfigurationService.removeCurrentAccountConfigurationChangeListener(
                    currentAccountConfigurationChangeListener
                );

                log.debug("BU: shutdown ok");
            } else {
                log.debug("BU: shutdown skipped, not started");
            }
        } catch (Exception e) {
            log.error("BU: shutdown failed", e);
            failure = e;
        } finally {
            super.shutDown();
            started = false;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        if (accountConfiguration == null) {
            return;
        }

        buChatService.sendMessage(
            "Bronzeman Unleashed is in BETA. Report bugs or feedback on our GitHub."
        );
    }


    @Subscribe
    public void onAccountHashChanged(AccountHashChanged event) {
        accountConfigurationService.onAccountHashChanged(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        buChatService.onGameStateChanged(event);
        buPartyService.onGameStateChanged(event);
        achievementDiaryService.onGameStateChanged(event);
        itemUnlockService.onGameStateChanged(event);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        accountConfigurationService.onConfigChanged(event);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        itemUnlockService.onItemContainerChanged(event);
    }

    @Subscribe
    public void onServerNpcLoot(ServerNpcLoot event) {
        itemUnlockService.onServerNpcLoot(event);
        lootValuationService.onServerNpcLoot(event);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        buChatService.onChatMessage(chatMessage);
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent scriptCallbackEvent) {
        buChatService.onScriptCallbackEvent(scriptCallbackEvent);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        grandExchangePolicy.onScriptPostFired(event);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        tradePolicy.onMenuOptionClicked(event);
        groundItemsPolicy.onMenuOptionClicked(event);
        playerOwnedHousePolicy.onMenuOptionClicked(event);
        playerVersusPlayerPolicy.onMenuOptionClicked(event);
        faladorPartyRoomPolicy.onMenuOptionClicked(event);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        buChatService.onVarbitChanged(event);
        achievementDiaryService.onVarbitChanged(event);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        shopPolicy.onWidgetLoaded(event);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        shopPolicy.onWidgetClosed(event);
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event) {
        groundItemsPolicy.onItemSpawned(event);
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        groundItemsPolicy.onItemDespawned(event);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event) {
        playerOwnedHousePolicy.onScriptPreFired(event);
    }

    @Subscribe
    public void onActorDeath(ActorDeath e) {
        playerVersusPlayerPolicy.onActorDeath(e);
    }

    @Subscribe
    public void onPlayerLootReceived(PlayerLootReceived e) {
        playerVersusPlayerPolicy.onPlayerLootReceived(e);
    }
}
