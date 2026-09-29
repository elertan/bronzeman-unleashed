# Architecture Overview

## Plugin Structure

```
BUPlugin (entry point)
├── BUPluginLifecycle components (ordered startup/shutdown)
│   ├── Core services (resources, account config, remote storage)
│   ├── Data providers (members, game rules, unlocked items, etc.)
│   ├── Domain services (panel, overlay, chat, item unlock, etc.)
│   └── Policies (GE, trade, shop, ground items, POH, PvP, etc.)
├── Event subscriptions (delegates to relevant services/policies)
└── Config (BUPluginConfig)
```

## Layers

### 1. Plugin Core
- `BUPlugin` - Main entry point, manages lifecycle and event routing
- `BUPluginConfig` - RuneLite config interface
- `BUPluginLifecycle` - Interface for managed startup/shutdown

### 2. Services Layer
Domain logic split by responsibility:
- `ItemUnlockService` - Core unlock detection and tracking
- `GameRulesService` - Game rules management
- `MemberService` - Group member management
- `BUEventService` - Event broadcasting for achievements
- `PolicyService` - Policy enforcement coordination
- etc.

### 3. Policies Layer
Enforce game rules by intercepting game events:
- Each policy extends `PolicyBase`
- Uses `PolicyContext` to check if rules should apply
- Can block/modify player actions via RuneLite event handlers

### 4. Data Providers Layer
Manage data state and Firebase synchronization:
- Extend `AbstractDataProvider`
- Depend on `RemoteStorageService`
- Provide `await()` for async initialization

### 5. Remote Storage Layer
Firebase Realtime Database abstraction:
- Port interfaces: `KeyValueStoragePort`, `ObjectStoragePort`, `KeyListStoragePort`, `ObjectListStoragePort`
- Firebase adapters implement ports with SSE streaming

### 6. UI Layer
Side panel with MVVM architecture:
- Views (Swing components) bind to ViewModels
- `BaseViewModel` manages listener lifecycle
- `Property<T>` for observable state

## Data Flow

```
RuneLite Event
    ↓
BUPlugin.onXxx() subscriber
    ↓
Service/Policy handler
    ↓
[If item unlock] ItemUnlockService → DataProvider → Firebase
    ↓
[If rule violation] Policy blocks action
    ↓
[If achievement] BUEventService → LastEventDataProvider → Firebase → Other clients
```

## Key Dependencies

- RuneLite Client API (`net.runelite.api.*`, `net.runelite.client.*`)
- Lombok (annotations for boilerplate)
- Gson (JSON serialization)
- OkHttp (HTTP client for Firebase)
- Firebase Realtime Database (real-time sync)

## Game rule: Recipe-derived unlocks

When `enableRecipeDerivedUnlocks` is enabled in `GameRules`, `ItemUnlockService` will unlock configured recipe results when all prerequisite items are already unlocked (explicit mappings in `RelatedItemsRegistry`).

Interaction with `onlyForTradeableItems`:
- When `onlyForTradeableItems` is enabled, recipe-derived unlocks are only applied for **tradeable recipe results**.
