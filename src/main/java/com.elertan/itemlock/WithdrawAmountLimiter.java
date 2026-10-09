package com.elertan.itemlock;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.function.LongConsumer;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.QuantityFormatter;

/**
 * Limits the Withdraw-X amount: the prompt shows the limit, and Enter with a higher amount closes
 * the prompt without withdrawing. How the prompt works (found with in-game logging): script 108
 * opens it with the title as argument 1; each key stores the raw text (for example "600" or "1k")
 * in varc MESLAYERINPUT while MESLAYERMODE is 7.
 */
@Singleton
public class WithdrawAmountLimiter implements KeyListener {

    private static final int OPEN_AMOUNT_PROMPT_SCRIPT_ID = 108;
    private static final int CHATBOX_INPUT_CLOSE_SCRIPT_ID = 138;
    private static final int AMOUNT_PROMPT_MODE = 7;
    private static final String AMOUNT_PROMPT_TITLE = "Enter amount:";

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;

    private volatile int pendingLimit = -1;
    private volatile LongConsumer onBlocked;
    // Also consume the typed and released events of the Enter that closed the prompt.
    private volatile boolean isConsumingEnter;

    /**
     * @param onBlocked gets the typed amount, on the client thread, when Enter with a higher amount
     *     closes the prompt
     */
    public void limitNextPrompt(int limit, LongConsumer onBlocked) {
        this.onBlocked = onBlocked;
        pendingLimit = limit;
    }

    public void clear() {
        pendingLimit = -1;
        onBlocked = null;
    }

    public void onScriptPreFired(ScriptPreFired event) {
        if (pendingLimit >= 0 && event.getScriptId() == OPEN_AMOUNT_PROMPT_SCRIPT_ID) {
            showLimitInTitle(event.getScriptEvent());
        }
    }

    private void showLimitInTitle(ScriptEvent scriptEvent) {
        if (scriptEvent == null) {
            return;
        }
        Object[] arguments = scriptEvent.getArguments();
        if (arguments != null && arguments.length > 1 && AMOUNT_PROMPT_TITLE.equals(arguments[1])) {
            arguments[1] = "Enter amount (max " + pendingLimit + "):";
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (isConsumingEnter && e.getKeyChar() == '\n') {
            e.consume();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ENTER) {
            return;
        }
        long typed = typedAmount();
        if (pendingLimit < 0 || typed <= pendingLimit) {
            return;
        }
        e.consume();
        isConsumingEnter = true;
        LongConsumer blocked = onBlocked;
        clear();
        clientThread.invoke(() -> {
            client.runScript(CHATBOX_INPUT_CLOSE_SCRIPT_ID);
            if (blocked != null) {
                blocked.accept(typed);
            }
        });
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (isConsumingEnter && e.getKeyCode() == KeyEvent.VK_ENTER) {
            e.consume();
            isConsumingEnter = false;
        }
    }

    /** The amount in the open amount prompt, or -1. */
    private long typedAmount() {
        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != AMOUNT_PROMPT_MODE) {
            return -1;
        }
        try {
            return QuantityFormatter.parseQuantity(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
        } catch (ParseException e) {
            // The game rejects it too.
            return -1;
        }
    }
}
