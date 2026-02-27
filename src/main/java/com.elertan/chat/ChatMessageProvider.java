package com.elertan.chat;

import com.elertan.MemberService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public final class ChatMessageProvider {
    private final MemberService memberService;
    private final Map<MessageKey, Supplier<String>> resolvers;

    @Inject
    public ChatMessageProvider(final MemberService memberService) {
        this.memberService = memberService;
        this.resolvers = new EnumMap<>(MessageKey.class);
        resolvers.put(MessageKey.STILL_LOADING_TEMPORARY_STRICT_GAME_RULES_ENFORCEMENT,
            () -> "Bronzeman Unleashed is still loading. Temporarily enforcing strict game rules to ensure integrity.");
        resolvers.put(MessageKey.STILL_LOADING_PLEASE_WAIT,
            () -> "Bronzeman Unleashed is still loading. Please wait a moment before interacting to ensure integrity.");
        resolvers.put(MessageKey.TRADE_RESTRICTION,
            () -> restrictionMsg("You are a %s with trade restrictions.",
                " You stand alone.", " You can only trade members of your group."));
        resolvers.put(MessageKey.GROUND_ITEM_TAKE_RESTRICTION,
            () -> restrictionMsg("You cannot take this item due to %s ground item restrictions.",
                "", " Only items of your group may be taken."));
        resolvers.put(MessageKey.GROUND_ITEM_CAST_RESTRICTION,
            () -> restrictionMsg("You cannot cast on this ground item due to %s restrictions.",
                "", " Only items of your group may be casted on."));
        resolvers.put(MessageKey.POH_ENTER_RESTRICTION,
            () -> restrictionMsg("You cannot enter this Player Owned House due to %s restrictions.",
                "", " You may only enter one owned by your group."));
        resolvers.put(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_RESTRICTION,
            () -> restrictionMsg("You cannot loot this player due to %s restrictions.",
                "", " You may only loot players of your group."));
        resolvers.put(MessageKey.PLAYER_VERSUS_PLAYER_LOOT_KEY_RESTRICTION,
            () -> restrictionMsg("You cannot take or bank any items from loot keys due to %s restrictions.",
                null, null));
        resolvers.put(MessageKey.ITEM_UNLOCKS_UNSUPPORTED_WORLD,
            () -> "Item unlocks are disabled on this world type. Seasonal or special mode worlds do not support adding new unlocked items.");
        resolvers.put(MessageKey.FALADOR_PARTY_ROOM_BALLOON_RESTRICTION,
            () -> restrictionMsg("You cannot burst the Falador Party Room balloons due to %s restrictions.",
                null, null));
        resolvers.put(MessageKey.SHOP_BUY_RESTRICTION,
            () -> restrictionMsg("You cannot buy this item from shops due to %s restrictions.",
                "", " Only items allowed by your group rules may be bought."));
    }

    public String messageFor(final MessageKey key) {
        Supplier<String> supplier = resolvers.get(key);
        if (supplier == null) throw new IllegalArgumentException("Unknown message key: " + key);
        return supplier.get();
    }

    private String restrictionMsg(String template, String soloSuffix, String groupSuffix) {
        boolean solo = isSolo();
        String base = String.format(template, solo ? "Bronzeman" : "Group Bronzeman");
        String suffix = solo ? soloSuffix : groupSuffix;
        return suffix != null ? base + suffix : base;
    }

    private boolean isSolo() {
        try { return memberService.isPlayingAlone(); }
        catch (Exception ignored) { return true; }
    }

    public enum MessageKey {
        STILL_LOADING_TEMPORARY_STRICT_GAME_RULES_ENFORCEMENT,
        STILL_LOADING_PLEASE_WAIT,
        TRADE_RESTRICTION,
        GROUND_ITEM_TAKE_RESTRICTION,
        POH_ENTER_RESTRICTION,
        GROUND_ITEM_CAST_RESTRICTION,
        PLAYER_VERSUS_PLAYER_LOOT_RESTRICTION,
        PLAYER_VERSUS_PLAYER_LOOT_KEY_RESTRICTION,
        ITEM_UNLOCKS_UNSUPPORTED_WORLD,
        FALADOR_PARTY_ROOM_BALLOON_RESTRICTION,
        SHOP_BUY_RESTRICTION,
    }
}
