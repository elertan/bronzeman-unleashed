package com.elertan;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("bronzemanunleashed")
public interface BUPluginConfig extends Config {

    String GROUP = "bronzemanunleashed";

    @ConfigSection(name = "Unlock Overlay", description = "Unlocked Item Overlay settings", position = 1)
    String overlaySection = "overlaySection";
    @ConfigSection(name = "Shop", description = "Controls the in-game shop settings", position = 2)
    String shopSection = "shopSection";
    String SHOW_UNLOCKED_ITEMS_INDICATOR_IN_SHOPS_KEY_NAME = "showUnlockedItemsIndicatorInShops";
    @ConfigSection(name = "Chat", description = "Controls the chat window settings", position = 3)
    String chatSection = "chatSection";
    @ConfigSection(name = "Party", description = "Controls the party settings", position = 4)
    String partySection = "partySection";
    @ConfigSection(name = "Minigames", description = "Controls minigame behavior", position = 5)
    String minigameSection = "minigameSection";
    @ConfigSection(name = "Debug", description = "Debug settings for testing", position = 99)
    String debugSection = "debugSection";
    String SHOULD_AUTOMATICALLY_JOIN_PARTY_KEY = "shouldAutomaticallyJoinParty";
    String SHOULD_CHANGE_TO_PARTY_EVEN_IF_ALREADY_IN_PARTY = "shouldChangeToPartyEvenIfAlreadyInParty";
    String ACCOUNT_CONFIG_MAP_JSON_KEY = "accountConfigMapJson";
    String AUTO_OPEN_ACCOUNT_CONFIGURATION_DISABLED_FOR_ACCOUNT_HASHES_JSON_KEY = "autoOpenAccountConfigurationDisabledForAccountHashesJson";

    @ConfigItem(keyName = "showUnlockOverlay", name = "Enabled", description = "Shows an overlay when a new item is acquired", section = overlaySection)
    default boolean showUnlockOverlay() {
        return true;
    }

    @ConfigItem(keyName = "showAcquiredByInOverlay", name = "Show acquired by", description = "When the Unlocked Item overlay pops up, should that include who acquired that item when playing group bronzeman mode?", section = overlaySection)
    default boolean showAcquiredByInUnlockOverlay() {
        return true;
    }

    @ConfigItem(keyName = "showAcquiredByInOverlayForSelf", name = "Show acquired by for self", description = "When the Unlocked Item overlay pops up, should that include who acquired that item when playing group bronzeman mode when its you?", section = overlaySection)
    default boolean showAcquiredByInUnlockOverlayForSelf() {
        return false;
    }

    @ConfigItem(keyName = "unlockOverlayFrameOuterColor", name = "Frame outer color", description = "The color used to draw the outer frame", section = overlaySection)
    default Color unlockOverlayFrameOuterColor() {
        return new Color(95, 85, 68);
    }

    @ConfigItem(keyName = "unlockOverlayFrameInnerColor", name = "Frame inner color", description = "The color used to draw the inner frame", section = overlaySection)
    default Color unlockOverlayFrameInnerColor() {
        return new Color(81, 69, 54);
    }

    @ConfigItem(keyName = "unlockOverlayItemTextColor", name = "Item text color", description = "The color used to draw the item text", section = overlaySection)
    default Color unlockOverlayItemTextColor() {
        return Color.WHITE;
    }

    @ConfigItem(keyName = "unlockOverlayItemVisibleDuration", name = "Item visible (ms)", description = "The duration in milliseconds that the item will be visible on the overlay", section = overlaySection)
    @Range(min = 10, max = 10000)
    default int unlockOverlayItemVisibleDuration() {
        return 1250;
    }

    @ConfigItem(keyName = "unlockOverlayOpenAndCloseDuration", name = "Open and close (ms)", description = "The duration in milliseconds that the overlay will be open and close", section = overlaySection)
    @Range(min = 10, max = 10000)
    default int unlockOverlayOpenAndCloseDuration() {
        return 800;
    }

    @ConfigItem(keyName = SHOW_UNLOCKED_ITEMS_INDICATOR_IN_SHOPS_KEY_NAME, name = "Show indicator in shops", description = "Whether to show an indicator in the shop item list indicating whether the item is unlocked or not", section = shopSection)
    default boolean showUnlockedItemsIndicatorInShops() {
        return true;
    }

    @ConfigItem(keyName = "showItemUnlocksInChat", name = "Show item unlocks", description = "Whether to show the unlock of an item in the chat", section = chatSection)
    default boolean showItemUnlocksInChat() {
        return true;
    }

    @ConfigItem(keyName = "useChatColor", name = "Use chat color", description = "Whether to use the below set chat color, otherwise will default to standard colors (only applies to main color)", section = chatSection)
    default boolean useChatColor() {
        return true;
    }

    @ConfigItem(keyName = "chatColorOpaque", name = "Chat color opaque", description = "The color of the plugin's chat when chatbox is opaque", section = chatSection)
    default Color chatColorOpaque() {
        return new Color(174, 100, 5);
    }

    @ConfigItem(keyName = "chatColorTransparent", name = "Chat color transparent", description = "The color of the plugin's chat when chatbox is transparent", section = chatSection)
    default Color chatColorTransparent() {
        return new Color(213, 155, 106);
    }

    @ConfigItem(keyName = "chatPlayerNameColor", name = "Player name color", description = "The color used for a player name in the chat", section = chatSection)
    default Color chatPlayerNameColor() {
        return new Color(124, 133, 247);
    }

    @ConfigItem(keyName = "chatItemNameColor", name = "Item name color", description = "The color used for a item name in the chat", section = chatSection)
    default Color chatItemNameColor() {
        return new Color(187, 40, 40);
    }

    @ConfigItem(keyName = "chatHighlightColor", name = "Highlight color", description = "The color used to highlight certain things in the chat", section = chatSection)
    default Color chatHighlightColor() {
        return new Color(187, 40, 40);
    }

    @ConfigItem(keyName = "chatNPCNameColor", name = "NPC name color", description = "The color used for a NPC in the chat", section = chatSection)
    default Color chatNPCNameColor() {
        return new Color(52, 165, 65);
    }

    @ConfigItem(keyName = "combatTaskColor", name = "Combat task color", description = "The color used for a combat task in the chat", section = chatSection)
    default Color chatCombatTaskColor() {
        return new Color(25, 157, 40);
    }

    @ConfigItem(keyName = "chatQuestNameNameColor", name = "Quest name color", description = "The color used for a quest name in the chat", section = chatSection)
    default Color chatQuestNameColor() {
        return new Color(13, 172, 242);
    }

    @ConfigItem(keyName = "chatRestrictionColor", name = "Restriction color", description = "The color used to notify you of a restriction that is in palce in the chat", section = chatSection)
    default Color chatRestrictionColor() {
        // This color is great with a transparent chatbox, but sucks for opaque
//        return new Color(255, 107, 104);
        return new Color(255, 74, 70);
    }

    @ConfigItem(keyName = "chatErrorColor", name = "Error color", description = "The color used for an error in the chat", section = chatSection)
    default Color chatErrorColor() {
        return new Color(255, 74, 70);
    }

    @ConfigItem(keyName = "useItemIconsInChat", name = "Use item icons", description = "Whether to prepend item icons before the item name in the chat", section = chatSection)
    default boolean useItemIconsInChat() {
        return true;
    }

    @ConfigItem(keyName = SHOULD_AUTOMATICALLY_JOIN_PARTY_KEY, name = "Auto-join party on login", description = "Whether to automatically join the party when you login on a Bronzeman character (when a party password is set)", section = partySection)
    default boolean shouldAutomaticallyJoinPartyOnLogin() {
        return true;
    }

    @ConfigItem(keyName = "hideUnlockOverlayInMinigames", name = "Hide unlock overlay", description = "Hides the item unlock overlay in minigames/instances (Inferno, Gauntlet, raids, etc.)", section = minigameSection)
    default boolean hideUnlockOverlayInMinigames() {
        return true;
    }

    @ConfigItem(keyName = "hideUnlockChatInMinigames", name = "Hide unlock chat", description = "Hides the item unlock chat message in minigames/instances", section = minigameSection)
    default boolean hideUnlockChatInMinigames() {
        return false;
    }

    @ConfigItem(keyName = SHOULD_CHANGE_TO_PARTY_EVEN_IF_ALREADY_IN_PARTY, name = "Auto-join even if already in party", description = "Whether to change to the party even if you are already in a party", section = partySection, hidden = true)
    default boolean shouldChangeToPartyEvenIfAlreadyInParty() {
        return false;
    }

    @ConfigItem(keyName = ACCOUNT_CONFIG_MAP_JSON_KEY, name = "Account config map json", description = "A map of account names to their respective config", hidden = true)
    default String accountConfigMapJson() {
        return null;
    }

    @ConfigItem(keyName = AUTO_OPEN_ACCOUNT_CONFIGURATION_DISABLED_FOR_ACCOUNT_HASHES_JSON_KEY, name = "No account configuration for account hashes json", description = "A list of account hashes to not automatically open up the account configuration for", hidden = true)
    default String autoOpenAccountConfigurationDisabledForAccountHashesJson() {
        return null;
    }

    @ConfigItem(keyName = "enableDebugCommands", name = "Enable debug commands", description = "Allows !bu: chat commands for testing (e.g. !bu:pet_following)", section = debugSection)
    default boolean enableDebugCommands() {
        return false;
    }
}
