---
date: 2026-01-19
status: approved
impl-spec: planspec:impl-spec
---

# Lombok Boilerplate Reduction

> **Next step:** `planspec:impl-spec planspec/designs/lombok-boilerplate-reduction.md`

## Problem

10 data classes have unnecessary boilerplate: per-field `@Getter` annotations and/or manual constructors that only assign fields. This adds ~80 lines of code that Lombok can generate.

## Success Criteria

**Must have:**
- All 10 classes refactored to use appropriate Lombok annotations
- Code compiles successfully
- All existing functionality preserved (getters work, constructors work)
- No runtime behavior change

**Quality attributes:**
- Reduced LOC (~80 lines)
- Consistent Lombok usage patterns across codebase

**Not building:**
- Not changing `FirebaseSSEDataLine` (non-static inner class with Gson)
- Not changing `GroundItemOwnedByKey` (has custom toString)
- Not changing classes with constructor logic beyond field assignment

## Approach

Replace verbose Lombok patterns with concise equivalents:
- `@Getter` per-field + manual constructor → `@Value` (immutable DTOs)
- `@Getter` per-field + `@AllArgsConstructor` → `@Value`
- `@Getter @Setter` per-field + `@AllArgsConstructor` → `@Data @AllArgsConstructor` (mutable DTOs)
- Custom toString present → `@Getter @AllArgsConstructor` only

### Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| Keep as-is | Unnecessary boilerplate, inconsistent patterns |
| Use records (Java 14+) | Project targets Java 8 |

## Design

### Changes

| # | File | Class | Before | After |
|---|------|-------|--------|-------|
| 1 | GameRulesEditorViewModel.java | Props | 4x @Getter + constructor | @Value |
| 2 | MainViewViewModel.java | ListItem | 4x @Getter + constructor | @Value |
| 3 | BUResourceService.java | BUModIcons | 1x @Getter + constructor | @Value |
| 4 | AccountConfiguration.java | (root) | 1x @Getter + constructor | @Value |
| 5 | ISOOffsetDateTime.java | (root) | 1x @Getter + constructor | @Getter @AllArgsConstructor |
| 6 | AchievementDiaryService.java | AchievementDiaryVarbitInfo | @AllArgsConstructor @EqualsAndHashCode + 2x @Getter | @Value |
| 7 | GroundItemsPolicy.java | GetClickedTileItemOutput | @AllArgsConstructor + 2x @Getter @NonNull | @Value |
| 8 | BUCommandService.java | CommandInfo | @AllArgsConstructor @Getter | @Value |
| 9 | GroundItemOwnedByData.java | (root) | @AllArgsConstructor + 3x @Getter @Setter | @Data @AllArgsConstructor |
| 10 | PolicyBase.java | PolicyContext | @AllArgsConstructor + 2x @Getter | @Value |

### Behavior

**Happy path:** Each class compiles with fewer annotations, same getters/constructors generated.

**Edge cases verified:**
- `GroundItemOwnedByData`: Gson deserialization works (uses Unsafe, not constructors)
- `PolicyContext`: @Nullable annotation preserved on field
- `GetClickedTileItemOutput`: @NonNull annotations preserved, fields become final (improvement)
- `ListItem`: Constructor visibility changes private→public (harmless, only created internally)
- `ISOOffsetDateTime`: Custom toString() preserved (not using @Value)

**Error handling:** N/A - compile-time changes only.

### Testing Requirements

- Compile the project successfully
- Run existing tests (if any)
- Manual verification not needed - Lombok generates identical bytecode

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Lombok version incompatibility | Low | Build failure | Project already uses Lombok extensively |
| Field annotation loss | Low | Runtime bugs | Verified: @Nullable/@NonNull preserved with @Value |

## Dependencies

- **Blocking:** None
- **Non-blocking:** None

## Open Questions

None.
