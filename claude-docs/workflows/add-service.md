# Adding a New Service

## Steps

### 1. Create Service Class

Location: `src/main/java/com.elertan/MyService.java`

```java
package com.elertan;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyService implements BUPluginLifecycle {

    @Inject
    private Client client;
    // Other injected dependencies

    @Override
    public void startUp() throws Exception {
        log.debug("MyService starting up");
        // Initialize resources
    }

    @Override
    public void shutDown() throws Exception {
        log.debug("MyService shutting down");
        // Cleanup resources
    }

    // Service methods
    public void doSomething() {
        // Implementation
    }
}
```

### 2. Register in BUPlugin

Edit `BUPlugin.java`:

```java
// Add injection
@Inject
private MyService myService;

// Add to lifecycle (in initLifecycleDependencies)
lifecycleDependencies.add(myService);
```

Order matters - add after dependencies, before dependents.

### 3. Wire Event Handlers (if needed)

If service needs RuneLite events:

```java
// In BUPlugin.java
@Subscribe
public void onSomeEvent(SomeEvent event) {
    myService.onSomeEvent(event);
}
```

### 4. Add Service Method to Handle Event

```java
// In MyService.java
public void onSomeEvent(SomeEvent event) {
    // Handle event
}
```

## Checklist

- [ ] Class implements `BUPluginLifecycle`
- [ ] `@Inject` for dependencies
- [ ] `@Slf4j` for logging
- [ ] Registered in `BUPlugin.lifecycleDependencies`
- [ ] Event handlers wired if needed
- [ ] Cleanup in `shutDown()`
