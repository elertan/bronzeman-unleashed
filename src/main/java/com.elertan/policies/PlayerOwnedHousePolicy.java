package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginLifecycle;
import com.elertan.BUSoundHelper;
import com.elertan.GameRulesService;
import com.elertan.MemberService;
import com.elertan.PolicyService;
import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.utils.TextUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
public class PlayerOwnedHousePolicy extends PolicyBase implements BUPluginLifecycle {

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private MemberService memberService;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private BUSoundHelper buSoundHelper;
    private volatile boolean shouldRecheckFriendsHouseInput = false;
    private String lastFriendsHouseEnteredName = null;
    private volatile boolean isStarted = false;

    @Inject
    public PlayerOwnedHousePolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {
        isStarted = true;
    }

    @Override
    public void shutDown() throws Exception {
        isStarted = false;
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
        String menuOption = event.getMenuOption();
        if (menuOption.equalsIgnoreCase("enter house")) {
            enforcePolicyEnterHouseMenuOptionClicked(event);
        }
        if (menuOption.equalsIgnoreCase("friend's house")) {
            enforcePolicyViewHouseMenuOptionClicked(event);
        }
    }

    private void enforcePolicyEnterHouseMenuOptionClicked(MenuOptionClicked event) {
        Widget buttonWidget = event.getWidget();
        if (buttonWidget == null) {
            log.error("Button widget is null played owned house policy");
            return;
        }
        Widget pohboardNameWidget = client.getWidget(InterfaceID.PohBoard.NAME);
        if (pohboardNameWidget == null) {
            log.error("Pohboard name widget is null played owned house policy");
            return;
        }
        int buttonWidgetIdx = buttonWidget.getIndex();
        Widget pohNameWidget = pohboardNameWidget.getChild(buttonWidgetIdx);
        if (pohNameWidget == null) {
            log.error("Poh name widget is null played owned house policy");
            return;
        }
        String pohNameWidgetText = pohNameWidget.getText();
        if (pohNameWidgetText == null) {
            log.error("Poh name widget text is null played owned house policy");
            return;
        }
        String playerName = TextUtils.sanitizePlayerName(pohNameWidgetText);
        Member member;
        try {
            member = memberService.getMemberByName(playerName);
        } catch (Exception ex) {
            buChatService.sendMessage(chatMessageProvider.messageFor(MessageKey.STILL_LOADING_PLEASE_WAIT));
            event.consume();
            buSoundHelper.playDisabledSound();
            return;
        }

        if (member != null) {
            return;
        }

        buChatService.sendMessage(chatMessageProvider.messageFor(MessageKey.POH_ENTER_RESTRICTION));
        event.consume();
        buSoundHelper.playDisabledSound();
    }

    private void enforcePolicyViewHouseMenuOptionClicked(MenuOptionClicked event) {
        log.info("Friend's house menu option clicked, waiting for friends house chatbox input ready");
        waitForFriendsHouseChatboxInputReady()
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Error waiting for friends house chatbox input ready", throwable);
                    return;
                }

                log.info("Friends house chatbox input ready, starting checker");
                startReadFriendsHouseChatboxInputLoop();
            });
    }

    private CompletableFuture<Void> waitForFriendsHouseChatboxInputReady() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicReference<Runnable> runnableRef = new AtomicReference<>();
        Runnable runnable = () -> {
            if (!isStarted) {
                Exception ex = new IllegalStateException("Policy is not started");
                future.completeExceptionally(ex);
                return;
            }
            boolean isReady = isFriendsHouseChatboxInputReady();
            if (isReady) {
                future.complete(null);
                return;
            }
            clientThread.invokeLater(runnableRef.get());
        };
        runnableRef.set(runnable);
        clientThread.invokeLater(runnable);

        return future;
    }

    private boolean isFriendsHouseChatboxInputReady() {
        Widget mesTextWidget = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
        if (mesTextWidget == null || mesTextWidget.isHidden()) {
            return false;
        }
        Widget mesText2Widget = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        if (mesText2Widget == null || mesText2Widget.isHidden()) {
            return false;
        }
        String mesTextWidgetText = mesTextWidget.getText();
        if (mesTextWidgetText == null) {
            return false;
        }
        String mesText2WidgetText = mesText2Widget.getText();
        if (mesText2WidgetText == null) {
            return false;
        }
        return mesTextWidgetText.equalsIgnoreCase("enter name:");
    }

    private volatile boolean isReadFriendsHouseChatboxInputLoopRunning = false;

    private void startReadFriendsHouseChatboxInputLoop() {
        if (isReadFriendsHouseChatboxInputLoopRunning) {
            return;
        }
        isReadFriendsHouseChatboxInputLoopRunning = true;

        AtomicReference<Runnable> monitorLoopRef = new AtomicReference<>();
        Runnable monitorLoop = () -> {
            if (!isReadFriendsHouseChatboxInputLoopRunning) {
                return;
            }

            if (!isFriendsHouseChatboxInputReady()) {
                log.info(
                    "reading friends house chatbox input halted because chatbox is not ready anymore");
                isReadFriendsHouseChatboxInputLoopRunning = false;
                return;
            }
            Widget mesText2Widget = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
            String mesText2WidgetText = mesText2Widget.getText();
            // Remove '*' from input
            String enteredName = mesText2WidgetText.replace("*", "");
            if (Objects.equals(enteredName, lastFriendsHouseEnteredName)) {
                clientThread.invokeLater(monitorLoopRef.get());
                return;
            }
            lastFriendsHouseEnteredName = enteredName;
            log.info("Friend's house entered name: {}", enteredName);

            clientThread.invokeLater(monitorLoopRef.get());
        };
        monitorLoopRef.set(monitorLoop);
        clientThread.invokeLater(monitorLoopRef.get());
    }

    public void onWidgetLoaded(WidgetLoaded event) {
//        int groupId = event.getGroupId();
//        if (groupId != InterfaceID.CHATBOX) {
//            return;
//        }
//        log.info("Chatbox loaded");
//        Widget mesTextWidget = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
//        if (mesTextWidget != null) {
//            log.info("mes text: {}", mesTextWidget.getText());
//        }
//        Widget mesText2Widget = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
//        if (mesText2Widget != null) {
//            log.info("mes text2: {}", mesText2Widget.getText());
//        }
    }

    public void onWidgetClosed(WidgetClosed event) {
//        int groupId = event.getGroupId();
//        if (groupId != InterfaceID.CHATBOX) {
//            return;
//        }
//        log.info("Chatbox close");
    }
}
