---
date: 2026-10-09
status: approved
issue: https://github.com/elertan/bronzeman-unleashed/issues/150
---

# Bronzeman Chat Icon Toggles

## Problem

The plugin always shows the Bronzeman helmet icon in the chat. Some players want the
restriction features, but do not want the icon in their chat, for example in
screenshots, clips or content. There is no setting to turn the icon off. Issue #150 asks
for a toggle similar to "Use item icons".

**How might we** let solo players use the Bronzeman restrictions without the helmet icon
in the chat, and let them choose where the icon shows?

## Objective

Add two toggles in the Chat section. They let the player choose where the helmet icon
shows. The restriction features, unlock messages and all other behavior stay the same.

**User:** a solo player who uses the restrictions but wants less icons in the chat.

## Scope

The helmet icon appears in three places today:

| # | Location | Code |
|---|----------|------|
| 1 | Own name in the chatbox input line | `BUChatService.manageIconOnChatbox` |
| 2 | Member names on public, clan, friends and private chat messages | `BUChatService.addIconToChatMessage` |
| 3 | Prefix on the plugin's own game messages (unlocks, restrictions, errors) | `BUChatService.queueFormattedMessage` |

The local player is also a member (`MemberService` adds them). For a solo player,
locations 1 and 2 show **the same name**: before and after they send a message.
Therefore one toggle controls locations 1 and 2 together, and one toggle controls
location 3.

The icon is client-side only. Other players never see it.

## Recommended Direction

| Toggle | Key | Controls | Default |
|--------|-----|----------|---------|
| Icon next to names | `useBronzemanIconOnNames` | Locations 1 and 2 | on |
| Icon on plugin messages | `useBronzemanIconOnMessages` | Location 3 | **off** |

This gives two clear choices: "how I look" and "how notifications look". Group players
get the same behavior: the names toggle controls their own name and teammates' names
together.

### Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| One toggle for all places | Does not give the flexible control that the user wants |
| One toggle for each location (3) | Locations 1 and 2 show the same name; a split gives an inconsistent result |
| "Chat icons" dropdown merged with "Use item icons" | Needs a config migration of `useItemIconsInChat` for little gain |

## Assumptions

1. Some players want the icon in one place but not in the other. If not, two toggles is
   one toggle with an extra click (low cost).
2. Plugin messages stay easy to identify without the helmet, because "Use chat color" is
   on by default and gives them their own color.
3. "Icon next to names" is **on** by default. "Icon on plugin messages" is **off** by
   default (decision after the in-game test). After the update, existing users see no
   helmet in front of plugin messages; the messages keep their own chat color.
4. The settings are global, not per account (same as all other chat settings).
5. When a setting changes, the chatbox input updates immediately. Messages already in
   the chat history keep their icon.
6. The mod icon sprite is still loaded when both toggles are off. This keeps the code
   simple and makes it possible to turn the icon back on without a restart.

## Design

### Config

`BUPluginConfig`, in `chatSection`, next to "Use item icons":

```java
String USE_BRONZEMAN_ICON_ON_NAMES_KEY = "useBronzemanIconOnNames";

@ConfigItem(keyName = USE_BRONZEMAN_ICON_ON_NAMES_KEY, name = "Icon next to names", description = "Whether to show the Bronzeman helmet icon next to your name and the names of group members in the chat", section = chatSection)
default boolean useBronzemanIconOnNames() {
    return true;
}

@ConfigItem(keyName = "useBronzemanIconOnMessages", name = "Icon on plugin messages", description = "Whether to show the Bronzeman helmet icon in front of the messages of this plugin in the chat", section = chatSection)
default boolean useBronzemanIconOnMessages() {
    return false;
}
```

Both keys are new. The earlier draft key `useBronzemanIconInChat` was never released, so
it is replaced without a migration.

### Behavior per location

1. **Chatbox input:** `manageIconOnChatbox` removes the icon when
   `useBronzemanIconOnNames` is off, with the same code path as shutdown. It adds the
   icon when the toggle is on.
2. **Member names:** `onChatMessage` calls `addIconToChatMessage` only when
   `useBronzemanIconOnNames` is on.
3. **Plugin messages:** `queueFormattedMessage` adds the icon prefix only when
   `useBronzemanIconOnMessages` is on. The message color handling does not change.

### Config change

`BUPlugin.onConfigChanged` forwards the event to `BUChatService.onConfigChanged`. That
method filters on group `BUPluginConfig.GROUP` and key `USE_BRONZEMAN_ICON_ON_NAMES_KEY`,
then calls `manageIconOnChatbox(false)` through `clientThread.invokeLater` (widget access
must be on the client thread). The messages toggle needs no handler: it is read when
each message is queued.

## Commands

```
Build + tests: ./gradlew build
Tests only:    ./gradlew test
Run client:    ./gradlew run
```

## Project Structure

```
src/main/java/com.elertan/BUPluginConfig.java    → two config items + key constant
src/main/java/com.elertan/BUChatService.java     → toggle checks + onConfigChanged
src/main/java/com.elertan/BUPlugin.java          → forward ConfigChanged
src/test/java/com/elertan/BUChatServiceTest.java → unit tests (JUnit 4)
planspec/designs/                                → this spec
```

## Code Style

Follow the existing code in `BUChatService`: guard clauses with early `return`,
`config.<flag>()` checks inline, no new abstractions. Example:

```java
if (config.useBronzemanIconOnNames()) {
    addIconToChatMessage(chatMessage);
}
```

Follow `AGENTS.md`: `log.debug` only, no blocking on the client thread, widget IDs from
`InterfaceID`.

## Testing Strategy

1. **Build:** `./gradlew build` passes, with no new warnings.
2. **Unit tests:** the three locations depend on `Client`, widgets and `MessageNode`, so
   unit tests have low value here. No new unit tests, unless the reviewer asks for them.
3. **Manual in-game test (by the user only, per `AGENTS.md`):**
   1. Defaults (names on, messages off): names look the same as before; plugin
      messages have no helmet.
   2. Names off: the icon goes away from the chatbox input immediately. A sent public
      chat message has no icon next to your name.
   3. Names off: plugin messages still have the icon.
   4. Messages off, names on: a restriction message has no icon prefix. Your name still
      has the icon. The message color is correct with "Use chat color" on and off.
   5. Both off: no helmet anywhere in new chat content.
   6. Names on again: the icon comes back on the input and on new messages.
   7. Names off, log out and log in: the icon does not come back.
   8. Names off, disable and enable the plugin: the icon does not come back.

## Boundaries

- **Always:** keep the defaults (names on, messages off); run `./gradlew build` before commit; let the user
  confirm in-game before the task is done.
- **Ask first:** adding more toggles; changing other chat settings; rewriting existing
  chat history.
- **Never:** rename released config keys; automate game input to test; add
  `Co-Authored-By: Claude` trailers.

## Not Doing (and Why)

1. A separate toggle for the input line: it shows the same name as the sent message.
2. Self vs teammates logic: the target user is solo.
3. A "Chat icons" dropdown with item icons: needs a config migration for little gain.
4. Changes to the unlock overlay: it already has its own toggle; scope is chat only.
5. Removing the icon from older chat history: new content only.
6. A hotkey or sidebar button: most users change this setting only one time.

## Success Criteria

1. "Icon next to names" (default on) and "Icon on plugin messages" (default off) are in
   the Chat section.
2. Each toggle controls only its own locations (see Scope).
3. With both on, the behavior is the same as before this change.
4. Changing "Icon next to names" updates the chatbox input without relog or plugin
   restart.
5. `./gradlew build` passes.
6. The user confirms test steps 1–8 in-game.

## Resolved Questions

1. Final labels: "Icon next to names" / "Icon on plugin messages". **Yes.**
2. Ask the author of issue #150 which combination they want? **No.** Assumption 1 is
   accepted without validation.

## Status Note

Implemented on branch `feat/toggle-bronzeman-chat-icon`. `./gradlew build` passes.
The user confirmed the in-game test on 2026-10-09. After the test, the default of
"Icon on plugin messages" changed to off.
