# Debug Commands

Chat commands for debugging and administration.

## Available Commands

| Command | Description |
|---------|-------------|
| `::bu reset` | Reset unlocked items (clears all unlocks) |

## Implementation

Commands handled by `BUCommandService`.

### Adding a Command

Edit `BUCommandService.java`:

```java
public boolean onChatMessage(ChatMessage chatMessage) {
    if (chatMessage.getType() != ChatMessageType.PUBLICCHAT) {
        return false;
    }

    String message = chatMessage.getMessage();

    if (message.equals("::bu reset")) {
        handleReset();
        return true;  // Consume event
    }

    if (message.equals("::bu mycommand")) {
        handleMyCommand();
        return true;
    }

    return false;  // Not consumed
}

private void handleMyCommand() {
    // Implementation
}
```

## Logging

Use `@Slf4j` annotation and `log.debug()`:

```java
log.debug("Processing command: {}", message);
log.debug("State: {}", state);
```

View logs:
- RuneLite → Settings → Developer → Debug logging
- Check `~/.runelite/logs/`

## State Inspection

Check current state:

```java
// In any service with @Inject
log.debug("Unlocked items: {}", unlockedItemsDataProvider.getData().size());
log.debug("Game rules: {}", gameRulesService.getGameRules());
log.debug("Members: {}", membersDataProvider.getData());
```
