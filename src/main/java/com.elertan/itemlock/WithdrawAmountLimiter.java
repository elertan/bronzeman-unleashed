package com.elertan.itemlock;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.ParseException;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.util.QuantityFormatter;

/**
 * Lowers the Withdraw-X amount to a limit. How the prompt works (found with in-game logging):
 * script 108 opens it with the title as argument 1; each key stores the raw text (for example
 * "600" or "1k") in varc MESLAYERINPUT while MESLAYERMODE is 7; Enter runs script 681 once,
 * which reads that text. Script 681 is shared by other prompts, such as the bank search.
 */
@Singleton
public class WithdrawAmountLimiter {

    private static final int OPEN_AMOUNT_PROMPT_SCRIPT_ID = 108;
    private static final int SUBMIT_PROMPT_SCRIPT_ID = 681;
    private static final int AMOUNT_PROMPT_MODE = 7;
    private static final String AMOUNT_PROMPT_TITLE = "Enter amount:";

    @Inject
    private Client client;

    private int pendingLimit = -1;

    public void limitNextPrompt(int limit) {
        pendingLimit = limit;
    }

    public void clear() {
        pendingLimit = -1;
    }

    public void onScriptPreFired(ScriptPreFired event) {
        if (pendingLimit < 0) {
            return;
        }
        int scriptId = event.getScriptId();
        if (scriptId == OPEN_AMOUNT_PROMPT_SCRIPT_ID) {
            showLimitInTitle(event.getScriptEvent());
        } else if (scriptId == SUBMIT_PROMPT_SCRIPT_ID) {
            lowerTypedAmount();
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

    private void lowerTypedAmount() {
        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != AMOUNT_PROMPT_MODE) {
            return;
        }
        int limit = pendingLimit;
        clear();
        long typed;
        try {
            typed = QuantityFormatter.parseQuantity(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
        } catch (ParseException e) {
            // The game rejects it too.
            return;
        }
        if (typed > limit) {
            client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(limit));
        }
    }
}
