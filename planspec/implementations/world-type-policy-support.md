---
date: 2026-01-31
design-spec: ../designs/world-type-policy-support.md
status: ready
executor: planspec:impl-spec-executor
---

# Implementation: World Type Policy Support

> **Execute with:** `planspec:impl-spec-executor planspec/implementations/world-type-policy-support.md`

## Overview

Create `WorldTypeService` to centralize world type checking, integrate into `PolicyBase` and `ItemUnlockService` to disable bronzeman features on unsupported worlds (Deadman, Seasonal, etc.).

**Design spec:** [world-type-policy-support.md](../designs/world-type-policy-support.md)
**Security reviews:** None

## Prerequisites

- [ ] None

---

## Phase 1: Create WorldTypeService

### Task 1.1: Create WorldTypeService class

**Context:** Central service for world type checking, replacing scattered logic in ItemUnlockService. Note: `BOUNTY` is a net-new addition (not in existing `ItemUnlockService.supportedWorldTypes`).

**Files:**
- Create: `src/main/java/com.elertan/WorldTypeService.java`

**Requirements:**
- `@Singleton` annotation
- Implement `BUPluginLifecycle` with empty `startUp()`/`shutDown()`
- Inject `Client`
- Define `SUPPORTED_WORLD_TYPES` using `Set.of()` with: `MEMBERS`, `PVP`, `BOUNTY`, `SKILL_TOTAL`, `HIGH_RISK`, `FRESH_START_WORLD`, `LAST_MAN_STANDING`
- Use `net.runelite.api.WorldType` (client API, not HTTP API). Note: this is a different enum than `net.runelite.http.api.worlds.WorldType` - verify all constants exist.
- Implement `isCurrentWorldSupported()` with javadoc noting client thread requirement:
  ```java
  /**
   * Checks if the current world type is supported for bronzeman features.
   * Must be called from client thread.
   *
   * @return true if policies and unlocks should apply, false otherwise
   */
  public boolean isCurrentWorldSupported() {
      EnumSet<WorldType> worldTypes = client.getWorldType();
      // Empty set returns true (vacuous truth via allMatch), which is expected
      return worldTypes.stream().allMatch(SUPPORTED_WORLD_TYPES::contains);
  }
  ```

**Acceptance Criteria:**
- [ ] Class compiles without errors
- [ ] Uses `net.runelite.api.WorldType` import
- [ ] `SUPPORTED_WORLD_TYPES` contains all 7 world types including `BOUNTY`
- [ ] Javadoc documents client thread requirement

**Dependencies:** None

---

## Phase 2: Integrate into PolicyBase

### Task 2.1: Add WorldTypeService to PolicyBase

**Context:** PolicyBase.createContext() needs to check world type before applying any policy rules.

**Files:**
- Modify: `src/main/java/com.elertan/policies/PolicyBase.java` - inject WorldTypeService, update createContext()

**Requirements:**
- Add `WorldTypeService` field (passed via constructor, not `@Inject` on field)
- Add `WorldTypeService` parameter to constructor
- In `createContext()`: add early check at the start of the method. If `!worldTypeService.isCurrentWorldSupported()`:
  - Log at debug level: `log.debug("Skipping policy - unsupported world type")`
  - Return `new PolicyContext(null, false)` (gameRules=null, mustEnforceStrictPolicies=false → shouldApplyForRules returns false)
- Note: This returns the same context as "no account config" case, but the log message distinguishes them for debugging

**Acceptance Criteria:**
- [ ] PolicyBase compiles without errors
- [ ] `createContext()` returns non-enforcing context on unsupported worlds
- [ ] Debug log message added for unsupported world case
- [ ] Existing policy behavior unchanged on supported worlds

**Dependencies:** Task 1.1

### Task 2.2: Update all policy constructors

**Context:** All policies extend PolicyBase and must pass WorldTypeService to super constructor. Verify complete list by searching for `extends PolicyBase`.

**Files:**
- Modify: `src/main/java/com.elertan/policies/GrandExchangePolicy.java`
- Modify: `src/main/java/com.elertan/policies/TradePolicy.java`
- Modify: `src/main/java/com.elertan/policies/ShopPolicy.java`
- Modify: `src/main/java/com.elertan/policies/GroundItemsPolicy.java`
- Modify: `src/main/java/com.elertan/policies/PlayerOwnedHousePolicy.java`
- Modify: `src/main/java/com.elertan/policies/PlayerVersusPlayerPolicy.java`
- Modify: `src/main/java/com.elertan/policies/FaladorPartyRoomPolicy.java`

**Requirements:**
- Add `WorldTypeService` parameter to each policy constructor
- Pass `WorldTypeService` to `super()` call

**Acceptance Criteria:**
- [ ] All 7 policies compile without errors
- [ ] Each policy passes WorldTypeService to PolicyBase constructor
- [ ] Verified no other classes extend PolicyBase (search: `extends PolicyBase`)

**Dependencies:** Task 2.1

### CHECKPOINT

Gate: All policies compile, createContext() returns non-enforcing context on unsupported worlds.

---

## Phase 3: Migrate ItemUnlockService

### Task 3.1: Replace world type checking in ItemUnlockService

**Context:** ItemUnlockService currently uses WorldService (HTTP API) and has its own supportedWorldTypes constant. Replace with WorldTypeService.

**Files:**
- Modify: `src/main/java/com.elertan/ItemUnlockService.java`

**Requirements:**
Order of operations: First add new injection, then update call sites, then remove old code.

1. Add `@Inject WorldTypeService worldTypeService`
2. In `unlockItem()`: replace `isCurrentWorldSupportedForUnlockingItems()` call with `worldTypeService.isCurrentWorldSupported()`
3. In `checkAndNotifyNonSupportedWorldType()`: replace `isCurrentWorldSupportedForUnlockingItems()` call with `worldTypeService.isCurrentWorldSupported()`
4. Remove `private boolean isCurrentWorldSupportedForUnlockingItems()` method
5. Remove `private static final Set<WorldType> supportedWorldTypes` constant
6. Remove `WorldService` injection (confirmed: only used in the removed method)
7. Remove unused imports: `net.runelite.http.api.worlds.WorldType`, `net.runelite.http.api.worlds.WorldResult`, `net.runelite.http.api.worlds.World`

**Acceptance Criteria:**
- [ ] ItemUnlockService compiles without errors
- [ ] No HTTP API world type imports remain
- [ ] `supportedWorldTypes` constant removed
- [ ] `isCurrentWorldSupportedForUnlockingItems()` method removed
- [ ] `WorldService` injection removed
- [ ] Both call sites use `worldTypeService.isCurrentWorldSupported()`

**Dependencies:** Task 1.1

### CHECKPOINT

Gate: Full build succeeds, all changes integrated.

---

## Completion Checklist

- [ ] All tasks completed
- [ ] Full build succeeds (`./gradlew build`)
- [ ] Design spec success criteria met:
  - [ ] Policies do not apply on unsupported world types
  - [ ] BOUNTY added to supported world types
  - [ ] Switch from WorldService (HTTP API) to client.getWorldType() (client API)
  - [ ] Single source of truth for supported world types via WorldTypeService

## Manual Testing

- [ ] Log into Deadman world → verify policies don't block actions (ground item pickup, trading)
- [ ] Log into normal Members world → verify policies still work
- [ ] Log into world with multiple types (e.g., Members + Skill Total) → verify supported
- [ ] Verify item unlocks still blocked on Deadman world (existing behavior preserved)
