---
date: 2026-10-09
design-spec: ../designs/bronzeman-chat-icon-toggle.md
status: completed
---

# Implementation: Bronzeman Chat Icon Toggles

## Overview

Add two config toggles that control where the Bronzeman helmet icon shows in the chat:
"Icon next to names" (default on) and "Icon on plugin messages" (default off).

**Design spec:** [bronzeman-chat-icon-toggle.md](../designs/bronzeman-chat-icon-toggle.md)
**Issue:** https://github.com/elertan/bronzeman-unleashed/issues/150
**Pull request:** https://github.com/elertan/bronzeman-unleashed/pull/181
**Security reviews:** None

## Prerequisites

- [x] None

---

## Phase 1: Toggles

### Task 1.1: Add config items

**Files:**
- Modify: `src/main/java/com.elertan/BUPluginConfig.java`

**Requirements:**
- Key constant `USE_BRONZEMAN_ICON_ON_NAMES_KEY = "useBronzemanIconOnNames"`
- `useBronzemanIconOnNames()`, name "Icon next to names", `chatSection`, default `true`
- `useBronzemanIconOnMessages()`, key `useBronzemanIconOnMessages`, name "Icon on plugin
  messages", `chatSection`, default `false`
- Both after "Use item icons"

**Status:** done

### Task 1.2: Apply toggles in the chat service

**Files:**
- Modify: `src/main/java/com.elertan/BUChatService.java`
- Modify: `src/main/java/com.elertan/BUPlugin.java`

**Requirements:**
- `onChatMessage`: call `addIconToChatMessage` only when `useBronzemanIconOnNames()`
- `manageIconOnChatbox`: remove the icon when `useBronzemanIconOnNames()` is off
- `queueFormattedMessage`: add the icon prefix only when `useBronzemanIconOnMessages()`
- New `onConfigChanged(ConfigChanged)`: on group `BUPluginConfig.GROUP` and key
  `USE_BRONZEMAN_ICON_ON_NAMES_KEY`, run `manageIconOnChatbox(false)` with
  `clientThread.invokeLater`
- `BUPlugin.onConfigChanged` forwards the event to `buChatService.onConfigChanged`

**Status:** done

---

## Verification

- [x] `./gradlew build` passes
- [x] In-game test by the user (design spec, Testing Strategy, step 3), 2026-10-09

## Review Notes

A code review of PR #181 found these items. They are not fixed in this implementation:

1. `queueFormattedMessage` throws when the helmet sprite is not loaded, also when
   "Icon on plugin messages" is off and the icon is not used.
2. `manageIconOnChatbox` logs `buModIcons is null` at ERROR on each plugin start, before
   the sprite is loaded. This existed before this change.
3. The deferred chatbox update from `onConfigChanged` can, in theory, run after
   `shutDown`. Low probability.
4. No unit test for the toggle logic.
