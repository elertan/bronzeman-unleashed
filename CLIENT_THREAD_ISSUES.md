# Client Thread Safety Notes

RuneLite's `Client` methods must be called from the client thread. This document tracks potential issues.

## Fixed Issues

### GroundItemsPolicy.cleanupExpiredGroundItems
- **Problem:** Called from `ScheduledExecutorService`, used `client.getAccountHash()`
- **Fix:** Wrapped in `clientThread.invoke()`

### ItemUnlockService.unlockItem
- **Problem:** `client.getAccountHash()` called in `CompletableFuture.whenComplete()` callback
- **Fix:** Cached accountHash before async call

### MemberService.MemberMapListener
- **Problem:** Data provider callbacks not on client thread, used `client.getAccountHash()`
- **Fix:** Wrapped callbacks in `clientThread.invoke()`

## Documented (Requires Caller Awareness)

### AccountConfigurationService
Methods marked with `// Requires: client thread`:
- `getCurrentAccountConfiguration()`
- `setCurrentAccountConfiguration()`
- `addCurrentAccountHashToAutoOpenConfigurationDisabled()`
- `isCurrentAccountAutoOpenAccountConfigurationEnabled()`

**Callers must ensure** they invoke these from:
- @Subscribe event handlers
- `clientThread.invoke()` or `clientThread.invokeLater()` blocks
- Plugin `startUp()`/`shutDown()` methods

## Safe (Documented)

### BUResourceService.getOrSetupItemImageModIconId
- `AsyncBufferedImage.onLoaded()` runs on client thread - no fix needed, added comment for clarity
