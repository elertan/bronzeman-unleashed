---
date: 2026-01-18
design-spec: ../designs/lombok-optimizations.md
status: ready
executor: planspec:impl-spec-executor
---

# Implementation: Lombok Optimizations

> **Execute with:** `planspec:impl-spec-executor planspec/implementations/lombok-optimizations.md`

## Overview

Apply Lombok annotations to reduce ~120 lines of boilerplate across model and event classes. Fixes hashCode bug in FirebaseSSE.

**Design spec:** [lombok-optimizations.md](../designs/lombok-optimizations.md)
**Security reviews:** None

## Prerequisites

- [x] Lombok already in project

---

## Phase 1: equals/hashCode Fixes

Isolated changes that fix bugs and remove boilerplate without affecting other code.

### Task 1.1: Fix FirebaseSSE equals/hashCode

**Context:** FirebaseSSE has a contract violation - equals() uses `type` field but hashCode() excludes it.

**Files:**
- Modify: `src/main/java/com.elertan/remote/firebase/FirebaseSSE.java`

**Requirements:**
- Add `@EqualsAndHashCode` annotation
- Remove manual equals() method (lines 27-39)
- Remove manual hashCode() method (lines 42-44)
- Keep manual toString() (uses `type.raw()`)

**Acceptance Criteria:**
- [ ] `@EqualsAndHashCode` present on class
- [ ] No manual equals/hashCode methods
- [ ] toString() unchanged

**Dependencies:** None

---

### Task 1.2: Simplify GroundItemOwnedByKey equals/hashCode

**Context:** GroundItemOwnedByKey has manual equals/hashCode using all 6 fields. Lombok can generate these.

**Files:**
- Modify: `src/main/java/com.elertan/models/GroundItemOwnedByKey.java`

**Requirements:**
- Add `@EqualsAndHashCode` annotation
- Remove manual equals() method (lines 100-114)
- Remove manual hashCode() method (line 118)
- Keep manual toString() (uses `toKey()`)
- Keep existing `@Builder`, `@AllArgsConstructor`, `@Getter`

**Acceptance Criteria:**
- [ ] `@EqualsAndHashCode` present on class
- [ ] No manual equals/hashCode methods
- [ ] toString() unchanged
- [ ] Other annotations preserved

**Dependencies:** None

---

### CHECKPOINT

Gate: Build passes before Phase 2.

---

## Phase 2: Model Classes

Convert mutable model classes to immutable @Value classes.

### Task 2.1: Convert UnlockedItem to @Value

**Context:** UnlockedItem has 5 fields with individual @Getter/@Setter but setters are never called.

**Files:**
- Modify: `src/main/java/com.elertan/models/UnlockedItem.java`

**Requirements:**
- Replace individual `@Getter`/`@Setter` with class-level `@Value`
- Remove `private` modifier from fields (Lombok adds it)
- Remove manual toString() method
- Keep `@JsonAdapter` annotation on `acquiredByAccountHash` field

**Acceptance Criteria:**
- [ ] `@Value` on class
- [ ] No individual @Getter/@Setter annotations
- [ ] No manual toString()
- [ ] Fields don't have explicit `private` (Lombok handles it)

**Dependencies:** None

---

### Task 2.2: Convert Member to @Value

**Context:** Member has 4 fields with individual @Getter/@Setter but setters are never called.

**Files:**
- Modify: `src/main/java/com.elertan/models/Member.java`

**Requirements:**
- Replace individual `@Getter`/`@Setter` with class-level `@Value`
- Remove `private` modifier from fields
- Keep `@JsonAdapter` annotation on `accountHash` field

**Acceptance Criteria:**
- [ ] `@Value` on class
- [ ] No individual @Getter/@Setter annotations
- [ ] Fields don't have explicit `private`

**Dependencies:** None

---

### Task 2.3: Convert GameRules to @Value with @Builder

**Context:** GameRules has 13 fields, a manual constructor, and manual toString(). Adding @Builder improves readability at call sites.

**Files:**
- Modify: `src/main/java/com.elertan/models/GameRules.java`

**Requirements:**
- Add `@Value`, `@Builder`, `@AllArgsConstructor(access = AccessLevel.PRIVATE)`
- Remove individual `@Getter`/`@Setter` annotations
- Remove `private` modifier from fields
- Remove manual constructor (lines 71-95)
- Remove manual toString() (lines 116-131)
- Refactor `createWithDefaults()` to use builder pattern
- Keep `@JsonAdapter` annotation on `lastUpdatedByAccountHash` field
- Add import for `lombok.AccessLevel`

**Acceptance Criteria:**
- [ ] `@Value`, `@Builder`, `@AllArgsConstructor(access = AccessLevel.PRIVATE)` on class
- [ ] No individual @Getter/@Setter annotations
- [ ] No manual constructor
- [ ] No manual toString()
- [ ] `createWithDefaults()` uses builder

**Dependencies:** None

---

### CHECKPOINT

Gate: Build passes before Phase 3.

---

## Phase 3: Event Classes

Fix base class, then simplify all child event classes.

### Task 3.1: Make BUEvent fields final

**Context:** BUEvent fields need to be final for children to properly use @EqualsAndHashCode(callSuper = true).

**Files:**
- Modify: `src/main/java/com.elertan/event/BUEvent.java`

**Requirements:**
- Add `private final` to `dispatchedFromAccountHash` field
- Add `private final` to `timestamp` field
- Keep existing constructor and @Getter annotations

**Acceptance Criteria:**
- [ ] Both fields are `private final`
- [ ] Constructor unchanged
- [ ] @Getter annotations unchanged

**Dependencies:** None

---

### Task 3.2: Simplify PetDropBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/PetDropBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields
- Keep constructor and `getType()` method

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.3: Simplify ValuableLootBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/ValuableLootBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.4: Simplify SkillLevelUpAchievementBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/SkillLevelUpAchievementBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.5: Simplify CombatTaskAchievementBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/CombatTaskAchievementBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.6: Simplify CombatLevelUpAchievementBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/CombatLevelUpAchievementBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.7: Simplify TotalLevelAchievementBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/TotalLevelAchievementBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.8: Simplify DiaryCompletionAchievementBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/DiaryCompletionAchievementBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### Task 3.9: Simplify QuestCompletionAchievementBUEvent

**Files:**
- Modify: `src/main/java/com.elertan/event/QuestCompletionAchievementBUEvent.java`

**Requirements:**
- Add class-level `@Getter`
- Add `@EqualsAndHashCode(callSuper = true)`
- Remove individual `@Getter` from fields

**Acceptance Criteria:**
- [ ] Class-level `@Getter` and `@EqualsAndHashCode(callSuper = true)`
- [ ] No individual @Getter on fields

**Dependencies:** Task 3.1

---

### CHECKPOINT

Gate: Build passes.

---

## Completion Checklist

- [ ] All tasks completed
- [ ] Build passes
- [ ] Design spec success criteria met:
  - [ ] 3 model classes use @Value
  - [ ] GameRules uses @Builder with private constructor
  - [ ] 8 event classes use class-level @Getter + @EqualsAndHashCode(callSuper = true)
  - [ ] BUEvent fields are final
  - [ ] Manual toString() removed from GameRules, UnlockedItem
  - [ ] Manual equals/hashCode removed from GroundItemOwnedByKey, FirebaseSSE
  - [ ] FirebaseSSE hashCode bug fixed
