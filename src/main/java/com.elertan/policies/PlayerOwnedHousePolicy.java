package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginConfig;
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
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

@Slf4j
@Singleton
public class PlayerOwnedHousePolicy extends PolicyBase implements BUPluginLifecycle {

    private static final int CHATBOX_INPUT_SCRIPT_ID = 112;
    private static final int CHATBOX_INPUT_CLOSE_SCRIPT_ID = 138;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private MemberService memberService;
    @Inject
    private BUChatService buChatService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private KeyManager keyManager;

    private volatile boolean isStarted = false;
    private volatile boolean isReadFriendsHouseChatboxInputLoopRunning = false;
    private String lastFriendsHouseEnteredName = null;
    private KeyListener keyListener;

    @Inject
    public PlayerOwnedHousePolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    @Override
    public void startUp() throws Exception {
        isStarted = true;

        keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    if (lastFriendsHouseEnteredName != null
                        && !lastFriendsHouseEnteredName.isEmpty()) {
                        e.consume();
                    }
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (lastFriendsHouseEnteredName != null
                        && !lastFriendsHouseEnteredName.isEmpty()) {
                        PolicyContext policyContext = createContext();
                        if (policyContext.isMustEnforceStrictPolicies()) {
                            enforcePolicyEnterKeyPressedOnFriendsHouseName(e);
                            return;
                        }
                        GameRules gameRules = policyContext.getGameRules();
                        if (gameRules == null || !gameRules.isPreventPlayedOwnedHouse()) {
                            return;
                        }
                        enforcePolicyEnterKeyPressedOnFriendsHouseName(e);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (lastFriendsHouseEnteredName != null
                        && !lastFriendsHouseEnteredName.isEmpty()) {
                        e.consume();
                    }
                }
            }
        };
        keyManager.registerKeyListener(keyListener);
    }

    @Override
    public void shutDown() throws Exception {
        keyManager.unregisterKeyListener(keyListener);
        keyListener = null;

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
        MenuAction menuAction = event.getMenuAction();
        String menuOption = event.getMenuOption();
        // Enter house via POH board
        if (menuOption.equalsIgnoreCase("enter house")) {
            enforcePolicyEnterHouseMenuOptionClicked(event);
        }
        // Enter house via menu option right click on portal
        if (menuOption.equalsIgnoreCase("friend's house")) {
            enforcePolicyViewHouseMenuOptionClicked(event);
        }
        // Potentially continue'ing

        if (menuAction.ordinal() == MenuAction.WIDGET_CONTINUE.ordinal()) {
            Widget widget = event.getWidget();
            if (widget == null) {
                return;
            }
            String text = widget.getText();
            if (text.equalsIgnoreCase("go to a friend's house")) {
                enforcePolicyGoToAFriendsHouseMenuOptionClicked(event);
            }
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

        boolean couldEnter = validateHouseEntryForPlayer(pohNameWidgetText);
        if (!couldEnter) {
            event.consume();
        }
    }

    private void enforcePolicyViewHouseMenuOptionClicked(MenuOptionClicked event) {
        log.debug(
            "Friend's house menu option clicked, waiting for friends house chatbox input ready");
        waitForFriendsHouseChatboxInputReady()
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Error waiting for friends house chatbox input ready", throwable);
                    return;
                }

                log.debug("Friends house chatbox input ready, starting checker");
                startReadFriendsHouseChatboxInputLoop();
            });
    }

    private void enforcePolicyGoToAFriendsHouseMenuOptionClicked(MenuOptionClicked event) {
        log.debug(
            "Go to a friend's house menu option clicked, waiting for friends house chatbox input ready");
        waitForFriendsHouseChatboxInputReady()
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Error waiting for friends house chatbox input ready", throwable);
                    return;
                }

                log.debug("Friends house chatbox input ready, starting checker");
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
                log.debug(
                    "reading friends house chatbox input halted because chatbox is not ready anymore");
                isReadFriendsHouseChatboxInputLoopRunning = false;
                return;
            }
            clientThread.invokeLater(monitorLoopRef.get());
        };
        monitorLoopRef.set(monitorLoop);
        clientThread.invokeLater(monitorLoopRef.get());
    }

    public void onScriptPreFired(ScriptPreFired event) {
        if (event.getScriptId() == CHATBOX_INPUT_SCRIPT_ID) {
            ScriptEvent scriptEvent = event.getScriptEvent();
            int typedChar = scriptEvent.getTypedKeyChar();
            if (typedChar == 0) {
                return;
            }
            if (typedChar == 8) {
                // 'Backspace'
                if (lastFriendsHouseEnteredName != null && !lastFriendsHouseEnteredName.isEmpty()) {
                    String newName = lastFriendsHouseEnteredName.substring(
                        0,
                        lastFriendsHouseEnteredName.length() - 1
                    );
                    if (newName.isEmpty()) {
                        newName = null;
                    }
                    setLastFriendsHouseEnteredName(newName);
                }
                return;
            }
            if (typedChar == 10) {
                return;
            }
            char typedCharChar = (char) typedChar;
            String newName =
                lastFriendsHouseEnteredName == null ? "" : lastFriendsHouseEnteredName;
            newName += typedCharChar;
            setLastFriendsHouseEnteredName(newName);
        } else if (event.getScriptId() == CHATBOX_INPUT_CLOSE_SCRIPT_ID) {
            isReadFriendsHouseChatboxInputLoopRunning = false;

            lastFriendsHouseEnteredName = null;
        }
    }

    private void setLastFriendsHouseEnteredName(String name) {
        if (Objects.equals(lastFriendsHouseEnteredName, name)) {
            return;
        }
        lastFriendsHouseEnteredName = name;
    }

    private void enforcePolicyEnterKeyPressedOnFriendsHouseName(KeyEvent e) {
        boolean couldEnter = validateHouseEntryForPlayer(lastFriendsHouseEnteredName);
        if (!couldEnter) {
            e.consume();
            clientThread.invoke(() -> client.runScript(
                CHATBOX_INPUT_CLOSE_SCRIPT_ID));
        }
    }

    private boolean validateHouseEntryForPlayer(String inputName) {
        String playerName = TextUtils.sanitizePlayerName(inputName);
        Member member;
        try {
            member = memberService.getMemberByName(playerName);
        } catch (Exception ex) {
            ChatMessageBuilder builder = new ChatMessageBuilder();
            builder.append(
                buPluginConfig.chatErrorColor(),
                chatMessageProvider.messageFor(MessageKey.STILL_LOADING_PLEASE_WAIT)
            );
            buChatService.sendMessage(builder.build());
            buSoundHelper.playDisabledSound();
            return false;
        }

        if (member == null) {
            ChatMessageBuilder builder = new ChatMessageBuilder();
            builder.append(
                buPluginConfig.chatRestrictionColor(),
                chatMessageProvider.messageFor(MessageKey.POH_ENTER_RESTRICTION)
            );
            buChatService.sendMessage(builder.build());
            buSoundHelper.playDisabledSound();
            return false;
        }

        return true;
    }
}
