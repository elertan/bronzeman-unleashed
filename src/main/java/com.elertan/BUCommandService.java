package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.chat.ChatMessageBuilder;

/**
 * Handles !bu: chat commands for plugin functionality and testing.
 */
@Slf4j
@Singleton
public class BUCommandService implements BUPluginLifecycle {

    private static final String COMMAND_PREFIX = "!bu:";

    @Inject
    private BUPluginConfig config;
    @Inject
    private PetDropService petDropService;
    @Inject
    private BUChatService buChatService;

    private final List<CommandInfo> commands = new ArrayList<>();
    private final List<CommandInfo> debugCommands = new ArrayList<>();

    @Override
    public void startUp() throws Exception {
        // General commands
        commands.add(new CommandInfo("help", "Show available commands", null, this::handleHelp));

        // Debug commands for pet detection testing
        debugCommands.add(new CommandInfo(
            "pet_following",
            "Simulate pet follow msg",
            null,
            this::handlePetFollowing
        ));
        debugCommands.add(new CommandInfo(
            "pet_backpack",
            "Simulate pet backpack msg",
            null,
            this::handlePetBackpack
        ));
        debugCommands.add(new CommandInfo(
            "pet_duplicate",
            "Simulate pet duplicate msg",
            null,
            this::handlePetDuplicate
        ));
        debugCommands.add(new CommandInfo(
            "pet_collection",
            "Simulate collection log",
            "<name>",
            this::handlePetCollection
        ));
    }

    @Override
    public void shutDown() throws Exception {
        commands.clear();
        debugCommands.clear();
    }

    /**
     * Process a chat message and handle !bu: commands.
     *
     * @param event the chat message event
     * @return true if the message was a command and was consumed
     */
    public boolean onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.PUBLICCHAT) {
            return false;
        }

        // Message format: "<img=X>PlayerName: !bu:command" or "PlayerName: !bu:command"
        String message = event.getMessage();
        int prefixIndex = message.toLowerCase().indexOf(COMMAND_PREFIX);
        if (prefixIndex < 0) {
            return false;
        }

        String commandString = message.substring(prefixIndex + COMMAND_PREFIX.length());
        String commandName;
        String argument = null;

        int colonIndex = commandString.indexOf(':');
        if (colonIndex >= 0) {
            commandName = commandString.substring(0, colonIndex).toLowerCase();
            argument = commandString.substring(colonIndex + 1);
        } else {
            commandName = commandString.toLowerCase();
        }

        log.info("Parsed command: '{}', argument: '{}'", commandName, argument);

        // Check regular commands first
        for (CommandInfo cmd : commands) {
            if (cmd.getName().equals(commandName)) {
                cmd.getHandler().handle(argument);
                return true;
            }
        }

        // Check debug commands if enabled
        if (config.enableDebugCommands()) {
            for (CommandInfo cmd : debugCommands) {
                if (cmd.getName().equals(commandName)) {
                    cmd.getHandler().handle(argument);
                    return true;
                }
            }
        }

        // Unknown command - show error
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(
            config.chatErrorColor(),
            "Unknown command: !bu:" + commandName + ". Use !bu:help for list."
        );
        buChatService.sendMessage(builder.build());
        return true;
    }

    private void handleHelp(String arg) {
        buChatService.sendMessage("[BU Commands]");
        for (CommandInfo cmd : commands) {
            buChatService.sendMessage(formatCommandHelp(cmd));
        }

        if (config.enableDebugCommands() && !debugCommands.isEmpty()) {
            buChatService.sendMessage("[Debug Commands]");
            for (CommandInfo cmd : debugCommands) {
                buChatService.sendMessage(formatCommandHelp(cmd));
            }
        }
    }

    private String formatCommandHelp(CommandInfo cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("!bu:").append(cmd.getName());
        if (cmd.getArgs() != null) {
            sb.append(":").append(cmd.getArgs());
        }
        sb.append(" - ").append(cmd.getDescription());
        return sb.toString();
    }

    private void handlePetFollowing(String arg) {
        log.debug("Debug: Simulating pet_following message");
        simulatePetMessage("You have a funny feeling like you're being followed.");
    }

    private void handlePetBackpack(String arg) {
        log.debug("Debug: Simulating pet_backpack message");
        simulatePetMessage("You feel something weird sneaking into your backpack.");
    }

    private void handlePetDuplicate(String arg) {
        log.debug("Debug: Simulating pet_duplicate message");
        simulatePetMessage("You have a funny feeling like you would have been followed...");
    }

    private void handlePetCollection(String petName) {
        log.info("[CMD] handlePetCollection called with: '{}'", petName);
        if (petName == null || petName.isEmpty()) {
            log.info("[CMD] petName is null/empty, showing usage");
            buChatService.sendMessage("Usage: !bu:pet_collection:<name>");
            return;
        }
        log.info("[CMD] calling simulatePetMessage");
        simulatePetMessage("New item added to your collection log: " + petName);
    }

    private void simulatePetMessage(String message) {
        log.info("[CMD] simulatePetMessage called with: '{}'", message);
        log.info("[CMD] calling petDropService.simulateGameMessage");
        petDropService.simulateGameMessage(message);
        log.info("[CMD] calling buChatService.sendMessage");
        buChatService.sendMessage("[Debug] Pet drop simulated");
        log.info("[CMD] simulatePetMessage done");
    }

    @AllArgsConstructor
    @Getter
    private static class CommandInfo {

        private final String name;
        private final String description;
        private final String args;
        private final CommandHandler handler;
    }

    @FunctionalInterface
    private interface CommandHandler {

        void handle(String argument);
    }
}
