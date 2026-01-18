---
date: 2026-01-18
status: approved
impl-spec: planspec:impl-spec
---

# Lombok Optimizations

> **Next step:** `planspec:impl-spec planspec/designs/lombok-optimizations.md`

## Problem

Excessive boilerplate in model and event classes - manual constructors, getters/setters, toString(), equals/hashCode that Lombok can generate. Also a bug in FirebaseSSE where hashCode() excludes the `type` field, violating the equals/hashCode contract.

## Success Criteria

**Must have:**
- 3 model classes (GameRules, Member, UnlockedItem) use `@Value`
- GameRules uses `@Builder` with private constructor, `createWithDefaults()` uses builder
- 8 event classes use class-level `@Getter` + `@EqualsAndHashCode(callSuper = true)`
- BUEvent fields made final
- Manual toString() removed from GameRules, UnlockedItem
- Manual equals/hashCode removed from GroundItemOwnedByKey, FirebaseSSE
- FirebaseSSE hashCode bug fixed
- Build passes

**Quality attributes:**
- Zero behavior change (except FirebaseSSE bug fix)
- ~120 lines of boilerplate removed

**Not building:**
- No @Value on event classes (inheritance constraint)
- No changes to GroundItemOwnedByKey/FirebaseSSE toString() (custom logic needed)
- No new tests

## Approach

Apply Lombok annotations in order of isolation/risk:
1. equals/hashCode fixes (isolated)
2. Model classes (no dependencies)
3. Event classes (requires base class fix)

### Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| @Value on event classes | Lombok can't generate super() calls for inheritance |
| @SuperBuilder for events | Invasive API change, overkill for simple event construction |
| @Data instead of @Value | Models are immutable, @Value is more appropriate |

## Design

### Files Changed

| File | Changes |
|------|---------|
| `remote/firebase/FirebaseSSE.java` | Add `@EqualsAndHashCode`, remove manual equals/hashCode |
| `models/GroundItemOwnedByKey.java` | Add `@EqualsAndHashCode`, remove manual equals/hashCode |
| `models/UnlockedItem.java` | Replace individual @Getter/@Setter with `@Value`, remove toString |
| `models/Member.java` | Replace individual @Getter/@Setter with `@Value` |
| `models/GameRules.java` | Replace with `@Value @Builder @AllArgsConstructor(access = PRIVATE)`, refactor createWithDefaults to use builder, remove toString |
| `event/BUEvent.java` | Add `final` to fields |
| `event/*BUEvent.java` (8 files) | Replace individual @Getter with class-level `@Getter`, add `@EqualsAndHashCode(callSuper = true)`, keep constructor |

### Specific Changes

#### FirebaseSSE.java
```java
@EqualsAndHashCode  // ADD
public class FirebaseSSE {
    // Remove lines 27-44 (manual equals/hashCode)
    // Keep toString() - uses type.raw()
}
```

#### GroundItemOwnedByKey.java
```java
@EqualsAndHashCode  // ADD
@Builder
@AllArgsConstructor
@Getter
public class GroundItemOwnedByKey {
    // Remove lines 100-119 (manual equals/hashCode)
    // Keep toString() - uses toKey()
}
```

#### UnlockedItem.java
```java
@Value  // REPLACE individual @Getter/@Setter
public class UnlockedItem {
    int id;
    String name;
    @JsonAdapter(AccountHashJsonAdapter.class)
    long acquiredByAccountHash;
    ISOOffsetDateTime acquiredAt;
    Integer droppedByNPCId;
    // Remove toString()
}
```

#### Member.java
```java
@Value  // REPLACE individual @Getter/@Setter
public class Member {
    @JsonAdapter(AccountHashJsonAdapter.class)
    long accountHash;
    MemberRole role;
    String displayName;
    ISOOffsetDateTime joinedAt;
}
```

#### GameRules.java
```java
@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GameRules {
    @JsonAdapter(AccountHashJsonAdapter.class)
    Long lastUpdatedByAccountHash;
    ISOOffsetDateTime lastUpdatedAt;
    boolean onlyForTradeableItems;
    boolean restrictGroundItems;
    boolean preventTradeOutsideGroup;
    boolean preventTradeLockedItems;
    boolean preventGrandExchangeBuyOffers;
    boolean preventPlayerOwnedHouse;
    boolean restrictPlayerVersusPlayerLoot;
    boolean restrictFaladorPartyRoomBalloons;
    boolean shareAchievementNotifications;
    Integer valuableLootNotificationThreshold;
    String partyPassword;

    public static GameRules createWithDefaults(Long lastUpdatedByAccountHash,
        ISOOffsetDateTime lastUpdatedAt) {
        return GameRules.builder()
            .lastUpdatedByAccountHash(lastUpdatedByAccountHash)
            .lastUpdatedAt(lastUpdatedAt)
            .onlyForTradeableItems(true)
            .restrictGroundItems(true)
            .preventTradeOutsideGroup(true)
            .preventTradeLockedItems(true)
            .preventGrandExchangeBuyOffers(true)
            .preventPlayerOwnedHouse(true)
            .restrictPlayerVersusPlayerLoot(false)
            .restrictFaladorPartyRoomBalloons(true)
            .shareAchievementNotifications(true)
            .valuableLootNotificationThreshold(100_000)
            .partyPassword(null)
            .build();
    }
    // Remove manual constructor and toString()
}
```

#### BUEvent.java
```java
public abstract class BUEvent {
    @JsonAdapter(AccountHashJsonAdapter.class)
    @Getter
    private final long dispatchedFromAccountHash;  // ADD final

    @Getter
    private final ISOOffsetDateTime timestamp;  // ADD final

    // Constructor stays
}
```

#### Event classes (8 files) - same pattern
```java
@Getter  // Class-level, REPLACE individual @Getter
@EqualsAndHashCode(callSuper = true)  // ADD
public class XxxBUEvent extends BUEvent {
    // Remove @Getter from each field
    private final Type field1;
    private final Type field2;

    // Constructor stays (calls super)
    // getType() stays
}
```

### Testing Requirements

- Build/compile check only
- Manual testing by user

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Gson deserialization fails | Low | High | Verified works with @Value |
| HashMap lookup breaks | Low | High | Same fields used |

## Dependencies

- None (Lombok already in project)

## Open Questions

None.
