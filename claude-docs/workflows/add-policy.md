# Adding a New Policy

Policies enforce game rules by intercepting player actions.

## Steps

### 1. Create Policy Class

Location: `src/main/java/com.elertan/policies/MyPolicy.java`

```java
package com.elertan.policies;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.PolicyService;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOptionClicked;

@Slf4j
public class MyPolicy extends PolicyBase {

    @Inject
    public MyPolicy(
        AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService,
        PolicyService policyService
    ) {
        super(accountConfigurationService, gameRulesService, policyService);
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        PolicyContext ctx = createContext();

        // Check if this rule should apply
        if (!ctx.shouldApplyForRules(rules -> rules.isMyRuleEnabled())) {
            return;
        }

        // Check if action should be blocked
        if (shouldBlock(event)) {
            event.consume();  // Blocks the action
            policyService.notifyBlocked("Cannot do this - item locked");
        }
    }

    private boolean shouldBlock(MenuOptionClicked event) {
        // Implement blocking logic
        return false;
    }
}
```

### 2. Add Rule to GameRules Model (if new rule)

Edit `models/GameRules.java`:

```java
@Getter @Setter
private boolean myRuleEnabled;
```

### 3. Register in BUPlugin

Edit `BUPlugin.java`:

```java
// Add injection
@Inject
private MyPolicy myPolicy;

// Add to lifecycle (in initLifecycleDependencies, policies section)
lifecycleDependencies.add(myPolicy);
```

### 4. Wire Event Handlers

```java
// In BUPlugin.java
@Subscribe
public void onMenuOptionClicked(MenuOptionClicked event) {
    // ... existing handlers
    myPolicy.onMenuOptionClicked(event);
}
```

### 5. Add UI for Rule Toggle (optional)

Update `GameRulesEditor` and `GameRulesEditorViewModel` to expose toggle.

## Common Events for Policies

| Event | Use Case |
|-------|----------|
| `MenuOptionClicked` | Block menu actions (trade, pickup, enter POH) |
| `WidgetLoaded/Closed` | Modify UI widgets (shops, GE) |
| `ScriptPostFired` | Intercept script results |
| `ScriptPreFired` | Modify script inputs |
| `ItemSpawned/Despawned` | Track ground items |

## Checklist

- [ ] Extends `PolicyBase`
- [ ] Constructor injects base dependencies
- [ ] Uses `createContext()` and `shouldApplyForRules()`
- [ ] Uses `event.consume()` to block actions
- [ ] Uses `policyService.notifyBlocked()` for feedback
- [ ] Registered in lifecycle
- [ ] Event handlers wired
- [ ] GameRules updated if new rule type
