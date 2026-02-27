package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.CommandExecuted;

@Slf4j
@Singleton
public class BUCommandService implements BUPluginLifecycle {
    @Inject private BUPluginConfig config;
    @Inject private PetDropService petDropService;
    @Inject private BUChatService buChatService;

    private final List<CommandInfo> commands = new ArrayList<>();
    private final List<CommandInfo> debugCommands = new ArrayList<>();

    @Override
    public void startUp() throws Exception {
        commands.add(new CommandInfo("help", "Show available commands", null, this::handleHelp));
        debugCommands.add(new CommandInfo("pet_following", "Simulate pet follow msg", null, this::handlePetFollowing));
        debugCommands.add(new CommandInfo("pet_backpack", "Simulate pet backpack msg", null, this::handlePetBackpack));
        debugCommands.add(new CommandInfo("pet_duplicate", "Simulate pet duplicate msg", null, this::handlePetDuplicate));
        debugCommands.add(new CommandInfo("pet_collection", "Simulate collection log", "<name>", this::handlePetCollection));
    }

    @Override
    public void shutDown() throws Exception {
        commands.clear();
        debugCommands.clear();
    }

    public void onCommandExecuted(CommandExecuted event) {
        if (!event.getCommand().equalsIgnoreCase("bu")) return;
        String[] args = event.getArguments();
        if (args.length == 0) {
            buChatService.sendErrorMessage("Please enter a subcommand. Use ::bu help for list.");
            return;
        }
        String commandName = args[0].toLowerCase();
        String argument = args.length > 1 ? args[1] : null;
        log.info("Parsed command: '{}', argument: '{}'", commandName, argument);

        Stream<CommandInfo> stream = config.enableDebugCommands()
            ? Stream.concat(commands.stream(), debugCommands.stream()) : commands.stream();
        stream.filter(c -> c.getName().equals(commandName)).findFirst().ifPresentOrElse(
            c -> c.getHandler().handle(argument),
            () -> buChatService.sendErrorMessage(
                "Unknown command: ::bu " + commandName + ". Use ::bu help for list."));
    }

    private void handleHelp(String arg) {
        buChatService.sendMessage("[Bronzeman Unleashed Commands]");
        commands.stream().map(this::formatCommandHelp).forEach(buChatService::sendMessage);
        if (!config.enableDebugCommands() || debugCommands.isEmpty()) return;
        buChatService.sendMessage("[Debug Commands]");
        debugCommands.stream().map(this::formatCommandHelp).forEach(buChatService::sendMessage);
    }

    private String formatCommandHelp(CommandInfo cmd) {
        StringBuilder sb = new StringBuilder("::bu ").append(cmd.getName());
        Optional.ofNullable(cmd.getArgs()).ifPresent(a -> sb.append(" ").append(a));
        return sb.append(" - ").append(cmd.getDescription()).toString();
    }

    private void handlePetFollowing(String arg) {
        simulatePetMessage("You have a funny feeling like you're being followed.");
    }

    private void handlePetBackpack(String arg) {
        simulatePetMessage("You feel something weird sneaking into your backpack.");
    }

    private void handlePetDuplicate(String arg) {
        simulatePetMessage("You have a funny feeling like you would have been followed...");
    }

    private void handlePetCollection(String petName) {
        if (petName == null || petName.isEmpty()) {
            buChatService.sendMessage("Usage: ::bu pet_collection <name>");
            return;
        }
        simulatePetMessage("New item added to your collection log: " + petName);
    }

    private void simulatePetMessage(String message) {
        petDropService.handleGameMessage(message);
        buChatService.sendMessage("[Debug] Pet drop simulated");
    }

    @Value
    private static class CommandInfo {
        String name;
        String description;
        String args;
        CommandHandler handler;
    }

    @FunctionalInterface
    private interface CommandHandler {
        void handle(String argument);
    }
}
