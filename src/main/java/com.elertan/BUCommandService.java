package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

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

    private final Map<String, CommandHandler> commands = new HashMap<>();
    private final Map<String, CommandHandler> debugCommands = new HashMap<>();

    @Override
    public void startUp() throws Exception {
        // Debug commands for pet detection testing
        debugCommands.put("pet_following", this::handlePetFollowing);
        debugCommands.put("pet_backpack", this::handlePetBackpack);
        debugCommands.put("pet_duplicate", this::handlePetDuplicate);
        debugCommands.put("pet_collection", this::handlePetCollection);
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

        String message = event.getMessage();
        if (!message.startsWith(COMMAND_PREFIX)) {
            return false;
        }

        String commandString = message.substring(COMMAND_PREFIX.length());
        String commandName;
        String argument = null;

        int colonIndex = commandString.indexOf(':');
        if (colonIndex >= 0) {
            commandName = commandString.substring(0, colonIndex);
            argument = commandString.substring(colonIndex + 1);
        } else {
            commandName = commandString;
        }

        // Check regular commands first
        CommandHandler handler = commands.get(commandName);
        if (handler != null) {
            handler.handle(argument);
            return true;
        }

        // Check debug commands if enabled
        if (config.enableDebugCommands()) {
            handler = debugCommands.get(commandName);
            if (handler != null) {
                handler.handle(argument);
                return true;
            }
        }

        return false;
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
        if (petName == null || petName.isEmpty()) {
            buChatService.sendMessage("Usage: !bu:pet_collection:PetName");
            return;
        }
        log.debug("Debug: Simulating pet collection log for: {}", petName);
        simulatePetMessage("New item added to your collection log: " + petName);
    }

    private void simulatePetMessage(String message) {
        // Create a mock ChatMessage-like call to PetDropService
        petDropService.simulateGameMessage(message);
        buChatService.sendMessage("[Debug] Simulated: " + message);
    }

    @FunctionalInterface
    private interface CommandHandler {
        void handle(String argument);
    }
}
