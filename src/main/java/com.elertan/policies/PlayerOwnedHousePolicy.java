package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.BUChatService;
import com.elertan.BUPluginLifecycle;
import com.elertan.GameRulesService;
import com.elertan.MemberService;
import com.elertan.PolicyService;
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
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

@Slf4j
@Singleton
public class PlayerOwnedHousePolicy extends PolicyBase implements BUPluginLifecycle {

    private static final int CHATBOX_INPUT_SCRIPT_ID = 112;
    private static final int CHATBOX_INPUT_CLOSE_SCRIPT_ID = 138;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private MemberService memberService;
    @Inject private BUChatService buChatService;
    @Inject private KeyManager keyManager;
    private volatile boolean isStarted = false;
    private volatile boolean isInputLoopRunning = false;
    private String lastEnteredName = null;
    private KeyListener keyListener;

    @Inject
    public PlayerOwnedHousePolicy(AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, PolicyService policyService) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    private boolean hasEnteredName() {
        return isInputLoopRunning && lastEnteredName != null && !lastEnteredName.isEmpty();
    }

    @Override
    public void startUp() throws Exception {
        isStarted = true;
        keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (hasEnteredName() && e.getKeyChar() == '\n') e.consume();
            }
            @Override
            public void keyPressed(KeyEvent e) {
                if (hasEnteredName() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    PolicyContext ctx = createContext();
                    if (ctx.shouldApplyForRules(GameRules::isPreventPlayerOwnedHouse)) {
                        enforcePolicyEnterKeyPressed(e);
                    }
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
                if (hasEnteredName() && e.getKeyCode() == KeyEvent.VK_ENTER) e.consume();
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
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        PolicyContext context = createContext();
        if (!context.shouldApplyForRules(GameRules::isPreventPlayerOwnedHouse)) return;

        String menuOption = event.getMenuOption();
        if (menuOption.equalsIgnoreCase("enter house")) {
            enforcePolicyEnterHouse(event);
        }
        if (menuOption.equalsIgnoreCase("friend's house")) {
            waitAndStartInputLoop();
        }
        if (event.getMenuAction().ordinal() == MenuAction.WIDGET_CONTINUE.ordinal()) {
            Widget widget = event.getWidget();
            if (widget != null && "go to a friend's house".equalsIgnoreCase(widget.getText())) {
                waitAndStartInputLoop();
            }
        }
    }

    private void enforcePolicyEnterHouse(MenuOptionClicked event) {
        Widget buttonWidget = event.getWidget();
        if (buttonWidget == null) { log.error("Button widget is null POH policy"); return; }
        Widget pohboardNameWidget = client.getWidget(InterfaceID.PohBoard.NAME);
        if (pohboardNameWidget == null) { log.error("Pohboard name widget is null POH policy"); return; }
        Widget pohNameWidget = pohboardNameWidget.getChild(buttonWidget.getIndex());
        if (pohNameWidget == null) { log.error("Poh name widget is null POH policy"); return; }
        String text = pohNameWidget.getText();
        if (text == null) { log.error("Poh name widget text is null POH policy"); return; }
        if (!validateHouseEntryForPlayer(text)) event.consume();
    }

    private void waitAndStartInputLoop() {
        waitForChatboxInputReady().whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("Error waiting for friends house chatbox input ready", throwable);
                return;
            }
            startInputLoop();
        });
    }

    private CompletableFuture<Void> waitForChatboxInputReady() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicReference<Runnable> ref = new AtomicReference<>();
        Runnable r = () -> {
            if (!isStarted) { future.completeExceptionally(new IllegalStateException("Not started")); return; }
            if (isChatboxInputReady()) { future.complete(null); return; }
            clientThread.invokeLater(ref.get());
        };
        ref.set(r);
        clientThread.invokeLater(r);
        return future;
    }

    private boolean isChatboxInputReady() {
        Widget mesText = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
        Widget mesText2 = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        if (mesText == null || mesText.isHidden() || mesText2 == null || mesText2.isHidden()) return false;
        return mesText.getText() != null && mesText2.getText() != null
            && mesText.getText().equalsIgnoreCase("enter name:");
    }

    private void startInputLoop() {
        if (isInputLoopRunning) return;
        isInputLoopRunning = true;
        AtomicReference<Runnable> ref = new AtomicReference<>();
        Runnable loop = () -> {
            if (!isInputLoopRunning) return;
            if (!isChatboxInputReady()) { isInputLoopRunning = false; return; }
            clientThread.invokeLater(ref.get());
        };
        ref.set(loop);
        clientThread.invokeLater(ref.get());
    }

    public void onScriptPreFired(ScriptPreFired event) {
        if (!accountConfigurationService.isBronzemanEnabled() || !isInputLoopRunning) return;
        if (event.getScriptId() == CHATBOX_INPUT_SCRIPT_ID) {
            ScriptEvent se = event.getScriptEvent();
            int ch = se.getTypedKeyChar();
            if (ch == 0 || ch == 10) return;
            if (ch == 8) {
                if (lastEnteredName != null && !lastEnteredName.isEmpty()) {
                    String n = lastEnteredName.substring(0, lastEnteredName.length() - 1);
                    setLastEnteredName(n.isEmpty() ? null : n);
                }
                return;
            }
            setLastEnteredName((lastEnteredName == null ? "" : lastEnteredName) + (char) ch);
        } else if (event.getScriptId() == CHATBOX_INPUT_CLOSE_SCRIPT_ID) {
            isInputLoopRunning = false;
            lastEnteredName = null;
        }
    }

    private void setLastEnteredName(String name) {
        if (!Objects.equals(lastEnteredName, name)) lastEnteredName = name;
    }

    private void enforcePolicyEnterKeyPressed(KeyEvent e) {
        if (!validateHouseEntryForPlayer(lastEnteredName)) {
            e.consume();
            clientThread.invoke(() -> client.runScript(CHATBOX_INPUT_CLOSE_SCRIPT_ID));
        }
    }

    private boolean validateHouseEntryForPlayer(String inputName) {
        String playerName = TextUtils.sanitizePlayerName(inputName);
        Member member;
        try { member = memberService.getMemberByName(playerName); }
        catch (Exception ex) { buChatService.sendRestrictionMessage(MessageKey.STILL_LOADING_PLEASE_WAIT); return false; }
        if (member == null) { buChatService.sendRestrictionMessage(MessageKey.POH_ENTER_RESTRICTION); return false; }
        return true;
    }
}
