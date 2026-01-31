---
date: 2026-01-31
status: approved
impl-spec: planspec:impl-spec
---

# World Type Policy Support

> **Next step:** `planspec:impl-spec planspec/designs/world-type-policy-support.md`

## Problem

Policies incorrectly enforce restrictions on special world types like Deadman Mode, where bronzeman rules shouldn't apply. Item unlocks are correctly blocked via `ItemUnlockService.supportedWorldTypes`, but policies in `PolicyBase` have no world type awareness - they only check game rules and account config.

This causes issues like blocking ground item pickup on Deadman worlds, even though Deadman has completely different game rules.

## Success Criteria

**Must have:**
- Policies do not apply on unsupported world types (DEADMAN, SEASONAL, BETA, NOSAVE_MODE, PVP_ARENA, QUEST_SPEEDRUNNING)
- BOUNTY added to supported world types (main game with minigame access)
- Switch from `WorldService` (HTTP API) to `client.getWorldType()` (client API)
- Single source of truth for supported world types via new `WorldTypeService`

**Quality attributes:**
- All calls are on client thread - no threading concerns
- Simpler code - no null checks for `WorldResult`

**Not building:**
- Per-policy world type overrides
- User notification when policies are disabled on unsupported worlds

## Approach

Create dedicated `WorldTypeService` for world type checks, inject into `PolicyBase` and `ItemUnlockService`.

### Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| Shared utility in PolicyBase | Couples ItemUnlockService to PolicyBase |
| Constants class + utility in each consumer | Duplicated logic, easy to diverge |

## Design

### Architecture Overview

```
WorldTypeService (new)
├── SUPPORTED_WORLD_TYPES constant
├── Client injection
└── isCurrentWorldSupported() → boolean

PolicyBase
├── Inject WorldTypeService
└── createContext() checks worldTypeService.isCurrentWorldSupported()
    → returns "don't apply" context if false

ItemUnlockService
├── Inject WorldTypeService
├── Remove supportedWorldTypes constant
├── Remove WorldService dependency
└── Replace isCurrentWorldSupportedForUnlockingItems()
    → call worldTypeService.isCurrentWorldSupported()
```

**Lifecycle:** `WorldTypeService` is stateless (reads from client), `startUp()`/`shutDown()` are empty. No registration needed in `BUPlugin` lifecycle list.

**Integration points:**
- `PolicyBase.createContext()` - early return if unsupported world
- `ItemUnlockService.unlockItem()` and `checkAndNotifyNonSupportedWorldType()` - use new service

### Data Model

Supported world types constant using `net.runelite.api.WorldType`:

```java
private static final Set<WorldType> SUPPORTED_WORLD_TYPES = Set.of(
    WorldType.MEMBERS,
    WorldType.PVP,
    WorldType.BOUNTY,
    WorldType.SKILL_TOTAL,
    WorldType.HIGH_RISK,
    WorldType.FRESH_START_WORLD,
    WorldType.LAST_MAN_STANDING
);
```

### Interfaces

```java
@Singleton
public class WorldTypeService implements BUPluginLifecycle {

    /**
     * Checks if the current world type is supported for bronzeman features.
     * Must be called from client thread.
     *
     * @return true if policies and unlocks should apply, false otherwise
     */
    public boolean isCurrentWorldSupported();
}
```

### Behavior

**WorldTypeService.isCurrentWorldSupported():**
```java
public boolean isCurrentWorldSupported() {
    EnumSet<WorldType> worldTypes = client.getWorldType();
    return worldTypes.stream().allMatch(SUPPORTED_WORLD_TYPES::contains);
}
```
- Returns `true` if all world types are in `SUPPORTED_WORLD_TYPES`
- Returns `false` if any world type is unsupported (e.g., `DEADMAN`)
- Empty set returns `true` (vacuous truth via `allMatch`)

**PolicyBase.createContext():**
- Early check: if `!worldTypeService.isCurrentWorldSupported()` → return context that causes `shouldApplyForRules()` to return `false`
- Existing logic unchanged otherwise

**ItemUnlockService:**
- `unlockItem()`: replace `isCurrentWorldSupportedForUnlockingItems()` with `worldTypeService.isCurrentWorldSupported()`
- `checkAndNotifyNonSupportedWorldType()`: same replacement
- Remove `WorldService` injection and `supportedWorldTypes` constant

### Testing Requirements

**Critical paths:**
- Policy does not block actions on Deadman/Seasonal worlds
- Policy still blocks actions on normal worlds (MEMBERS, PVP, etc.)
- Item unlocks still work on supported worlds
- Item unlocks still blocked on unsupported worlds

**Edge cases:**
- World with multiple types (e.g., `MEMBERS` + `DEADMAN`) → unsupported
- World with only supported types → supported

**Integration points:**
- `GroundItemsPolicy` - verify ground item pickup allowed on Deadman
- `TradePolicy` - verify trading allowed on Deadman
- `ItemUnlockService` - verify unlocks blocked on Deadman

**Manual testing:**
- Log into Deadman world, verify policies don't fire
- Log into normal world, verify policies still work

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| New RuneLite WorldType added | Low | Supported by default (in SUPPORTED set logic) | Monitor RuneLite updates |

## Dependencies

- **Blocking:** None
- **Non-blocking:** None

## Open Questions

None.
