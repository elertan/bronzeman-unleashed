package com.elertan.chat;

import com.elertan.MemberService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Composes chat messages based on game state. Designed for both error and future non-error
 * messages.
 */
@Singleton
public final class ChatMessageProvider {

    private final MemberService memberService;
    private final Map<MessageKey, Supplier<String>> resolvers;

    @Inject
    public ChatMessageProvider(final MemberService memberService) {
        this.memberService = memberService;
        this.resolvers = new EnumMap<>(MessageKey.class);
        this.resolvers.put(
            MessageKey.STILL_LOADING_TEMPORARY_STRICT_GAME_RULES_ENFORCEMENT,
            this::stillLoadingTemporaryStrictGameRulesEnforcement
        );
        this.resolvers.put(
            MessageKey.STILL_LOADING_PLEASE_WAIT,
            this::stillLoadingPleaseWaitError
        );
        this.resolvers.put(MessageKey.TRADE_RESTRICTION, this::tradeRestrictionMessage);
        this.resolvers.put(
            MessageKey.GROUND_ITEM_TAKE_RESTRICTION,
            this::groundItemTakeRestrictionMessage
        );
        this.resolvers.put(
            MessageKey.GROUND_ITEM_CAST_RESTRICTION,
            this::groundItemCastRestrictionMessage
        );
        this.resolvers.put(
            MessageKey.POH_ENTER_RESTRICTION,
            this::pohEnterRestrictionMessage
        );
    }

    /**
     * Returns a message for the given key. Never returns null.
     */
    public String messageFor(final MessageKey key) {
        final Supplier<String> supplier = resolvers.get(key);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown message key: " + key);
        }
        return supplier.get();
    }

    private boolean isSolo() {
        try {
            return memberService.isPlayingAlone();
        } catch (Exception ignored) {
            // Fail safe to solo semantics if the service is unavailable.
            return true;
        }
    }

    private String getIdentity(boolean isSolo) {
        if (isSolo) {
            return "Bronzeman";
        } else {
            return "Group Bronzeman";
        }
    }

    private String stillLoadingTemporaryStrictGameRulesEnforcement() {
        return "Bronzeman Unleashed is still loading. Temporarily enforcing strict game rules to ensure integrity.";
    }

    private String stillLoadingPleaseWaitError() {
        return "Bronzeman Unleashed is still loading. Please wait a moment before interacting to ensure integrity.";
    }

    private String tradeRestrictionMessage() {
        boolean isSolo = isSolo();
        String identity = getIdentity(isSolo);
        return String.format(
            "You are a %s with trade restrictions.%s",
            identity,
            isSolo ? " You stand alone." : " You can only trade members of your group."
        );
    }

    private String groundItemTakeRestrictionMessage() {
        boolean isSolo = isSolo();
        String identity = getIdentity(isSolo);
        return String.format(
            "You cannot take this item due to %s ground item restrictions.%s",
            identity,
            isSolo ? "" : " Only items of your group may be taken."
        );
    }

    private String groundItemCastRestrictionMessage() {
        boolean isSolo = isSolo();
        String identity = getIdentity(isSolo);
        return String.format(
            "You cannot cast on this ground item due to %s restrictions.%s",
            identity,
            isSolo ? "" : " Only items of your group may be casted on."
        );
    }

    private String pohEnterRestrictionMessage() {
        boolean isSolo = isSolo();
        String identity = getIdentity(isSolo);
        return String.format(
            "You cannot enter this Played Owned House due to %s restrictions.%s",
            identity,
            isSolo ? "" : " You may only enter one owned by your group."
        );
    }

    /**
     * Keys for message lookups. Extend with non-error keys later without changing call sites.
     */
    public enum MessageKey {
        STILL_LOADING_TEMPORARY_STRICT_GAME_RULES_ENFORCEMENT,
        STILL_LOADING_PLEASE_WAIT,
        TRADE_RESTRICTION,
        GROUND_ITEM_TAKE_RESTRICTION,
        POH_ENTER_RESTRICTION,
        GROUND_ITEM_CAST_RESTRICTION
    }
}
