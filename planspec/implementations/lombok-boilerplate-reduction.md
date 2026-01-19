---
date: 2026-01-19
design-spec: ../designs/lombok-boilerplate-reduction.md
status: in-progress
executor: planspec:impl-spec-executor
---

# Implementation: Lombok Boilerplate Reduction

> **Execute with:** `planspec:impl-spec-executor planspec/implementations/lombok-boilerplate-reduction.md`

## Overview

Refactor 10 data classes to use concise Lombok annotations (@Value, @Data) instead of verbose per-field annotations and manual constructors.

**Design spec:** [lombok-boilerplate-reduction.md](../designs/lombok-boilerplate-reduction.md)
**Security reviews:** None

## Prerequisites

- [ ] None - Lombok already in use throughout codebase

## Phase 1: Refactor Data Classes

All tasks are independent - can be executed in any order.

### Task 1.1: Refactor GameRulesEditorViewModel.Props to @Value

**Context:** Inner class with 4 fields, each with @Getter + manual constructor

**Files:**
- Modify: `src/main/java/com.elertan/panel/components/GameRulesEditorViewModel.java`

**Requirements:**
- Replace per-field @Getter annotations with class-level @Value
- Remove manual constructor
- Remove `private final` from fields (implied by @Value)

**Acceptance Criteria:**
- [ ] Class annotated with @Value
- [ ] No per-field @Getter annotations
- [ ] No manual constructor
- [ ] Fields declared without `private final`

**Dependencies:** None

---

### Task 1.2: Refactor MainViewViewModel.ListItem to @Value

**Context:** Inner class with 4 fields, each with @Getter + private constructor

**Files:**
- Modify: `src/main/java/com.elertan/panel/screens/main/unlockedItems/items/MainViewViewModel.java`

**Requirements:**
- Replace per-field @Getter annotations with class-level @Value
- Remove manual constructor
- Note: Constructor visibility changes private→public (harmless)

**Acceptance Criteria:**
- [ ] Class annotated with @Value
- [ ] No per-field @Getter annotations
- [ ] No manual constructor

**Dependencies:** None

---

### Task 1.3: Refactor BUResourceService.BUModIcons to @Value

**Context:** Inner class with 1 field + @Getter + constructor

**Files:**
- Modify: `src/main/java/com.elertan/BUResourceService.java`

**Requirements:**
- Replace @Getter + constructor with @Value

**Acceptance Criteria:**
- [ ] Class annotated with @Value
- [ ] No per-field @Getter annotation
- [ ] No manual constructor

**Dependencies:** None

---

### Task 1.4: Refactor AccountConfiguration to @Value

**Context:** Root class with 1 field + @Getter + constructor

**Files:**
- Modify: `src/main/java/com.elertan/models/AccountConfiguration.java`

**Requirements:**
- Replace @Getter + constructor with @Value

**Acceptance Criteria:**
- [ ] Class annotated with @Value
- [ ] No per-field @Getter annotation
- [ ] No manual constructor

**Dependencies:** None

---

### Task 1.5: Refactor ISOOffsetDateTime to @Getter @AllArgsConstructor

**Context:** Root class with custom toString() - cannot use @Value

**Files:**
- Modify: `src/main/java/com.elertan/models/ISOOffsetDateTime.java`

**Requirements:**
- Add class-level @Getter and @AllArgsConstructor
- Remove per-field @Getter
- Remove manual constructor
- KEEP custom toString() method

**Acceptance Criteria:**
- [ ] Class annotated with @Getter @AllArgsConstructor
- [ ] No per-field @Getter annotation
- [ ] No manual constructor
- [ ] Custom toString() still present

**Dependencies:** None

---

### Task 1.6: Refactor AchievementDiaryVarbitInfo to @Value

**Context:** Inner class with @AllArgsConstructor @EqualsAndHashCode + per-field @Getter

**Files:**
- Modify: `src/main/java/com.elertan/AchievementDiaryService.java`

**Requirements:**
- Replace @AllArgsConstructor @EqualsAndHashCode + per-field @Getter with @Value
- @Value = @Getter + @AllArgsConstructor + @EqualsAndHashCode + @ToString

**Acceptance Criteria:**
- [ ] Class annotated with @Value only
- [ ] No @AllArgsConstructor, @EqualsAndHashCode, or per-field @Getter

**Dependencies:** None

---

### Task 1.7: Refactor GetClickedTileItemOutput to @Value

**Context:** Inner class with @AllArgsConstructor + per-field @Getter @NonNull

**Files:**
- Modify: `src/main/java/com.elertan/policies/GroundItemsPolicy.java`

**Requirements:**
- Replace @AllArgsConstructor + per-field @Getter with @Value
- Keep @NonNull annotations on fields

**Acceptance Criteria:**
- [ ] Class annotated with @Value only
- [ ] No @AllArgsConstructor or per-field @Getter
- [ ] @NonNull annotations preserved on fields

**Dependencies:** None

---

### Task 1.8: Refactor CommandInfo to @Value

**Context:** Inner class with @AllArgsConstructor @Getter at class level

**Files:**
- Modify: `src/main/java/com.elertan/BUCommandService.java`

**Requirements:**
- Replace @AllArgsConstructor @Getter with @Value

**Acceptance Criteria:**
- [ ] Class annotated with @Value only
- [ ] No @AllArgsConstructor or @Getter annotations

**Dependencies:** None

---

### Task 1.9: Refactor GroundItemOwnedByData to @Data @AllArgsConstructor

**Context:** Mutable class (has setters) - use @Data not @Value

**Files:**
- Modify: `src/main/java/com.elertan/models/GroundItemOwnedByData.java`

**Requirements:**
- Replace per-field @Getter @Setter with class-level @Data
- Keep @AllArgsConstructor (needed for constructor calls)
- Keep field-level @JsonAdapter and @NonNull annotations

**Acceptance Criteria:**
- [ ] Class annotated with @Data @AllArgsConstructor
- [ ] No per-field @Getter or @Setter annotations
- [ ] @JsonAdapter and @NonNull annotations preserved on fields

**Dependencies:** None

---

### Task 1.10: Refactor PolicyContext to @Value

**Context:** Inner class with @AllArgsConstructor + per-field @Getter, has @Nullable annotation

**Files:**
- Modify: `src/main/java/com.elertan/policies/PolicyBase.java`

**Requirements:**
- Replace @AllArgsConstructor + per-field @Getter with @Value
- Keep @Nullable annotation on gameRules field
- Keep shouldApplyForRules() method (unaffected)

**Acceptance Criteria:**
- [ ] Class annotated with @Value only
- [ ] No @AllArgsConstructor or per-field @Getter
- [ ] @Nullable annotation preserved on gameRules field

**Dependencies:** None

---

### Task 1.11: Verify compilation

**Context:** Ensure all changes compile correctly

**Files:**
- None (verification only)

**Requirements:**
- Run Gradle build
- Fix any compilation errors

**Acceptance Criteria:**
- [ ] `./gradlew build` succeeds

**Dependencies:** Tasks 1.1-1.10

---

### CHECKPOINT

Gate: Build succeeds before completion.

---

## Completion Checklist

- [ ] All 10 classes refactored
- [ ] Build passes
- [ ] Design spec success criteria met:
  - [ ] All 10 classes refactored to use appropriate Lombok annotations
  - [ ] Code compiles successfully
  - [ ] All existing functionality preserved
  - [ ] No runtime behavior change
