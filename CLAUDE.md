# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RuneLite plugin for "Bronzeman Unleashed" - a custom game mode for Old School RuneScape. Tracks item unlocks, enforces
customizable game rules, and syncs data via Firebase for group play.

## Build & Test Commands

```bash
./gradlew build          # Build the plugin
./gradlew test           # Run tests
./gradlew clean build    # Clean rebuild
```

## Architecture

### Plugin Structure

- `BUPlugin.java` - Main entry point, extends RuneLite's `Plugin`, wires all dependencies via `BUPluginLifecycle`
  interface
- `BUPluginConfig.java` - Configuration interface for plugin settings

### Key Services (all implement `BUPluginLifecycle`)

- `ItemUnlockService` - Core logic for tracking/unlocking items based on inventory changes, NPC loot, containers
- `RemoteStorageService` - Manages Firebase connection, provides storage ports for different data types
- `AccountConfigurationService` - Handles per-account configuration
- `GameRulesService` - Manages custom game rules (tradeable-only, PvP restrictions, etc.)
- `MemberService` - Group member management
- `PolicyService` - Enforces game rules

### Policy Classes (in `/policies/`)

Enforce game restrictions by intercepting menu clicks, widget events:

- `GrandExchangePolicy`, `TradePolicy`, `ShopPolicy`, `GroundItemsPolicy`
- `PlayerOwnedHousePolicy`, `PlayerVersusPlayerPolicy`, `FaladorPartyRoomPolicy`

### Data Layer (in `/data/`)

Data providers bridge remote storage with local state:

- `UnlockedItemsDataProvider`, `MembersDataProvider`, `GameRulesDataProvider`
- `GroundItemOwnedByDataProvider`, `LastEventDataProvider`

### Remote Storage (in `/remote/`)

Firebase integration with SSE streaming:

- `FirebaseRealtimeDatabase` - Core Firebase API client
- `FirebaseSSEStream` - Real-time event streaming
- Storage adapters in `/remote/firebase/storageAdapters/`

### UI Layer (in `/panel/`)

MVVM pattern with ViewModels:

- `BUPanel` - Main sidebar panel
- Screens in `/panel/screens/` with corresponding `*ViewModel` classes

## Key Patterns

- **Lifecycle Management**: All services implement `BUPluginLifecycle` with `startUp()`/`shutDown()` methods. Dependency
  order matters in `BUPlugin.initLifecycleDependencies()`
- **Event Handling**: RuneLite events subscribed in `BUPlugin`, delegated to appropriate services
- **Async Operations**: Use `CompletableFuture` for async Firebase operations
- **State Listeners**: `ConcurrentLinkedQueue<Consumer<State>>` pattern for state change notifications

## Source Directory Note

Source code is in `src/main/java/com.elertan/` (unusual package naming with dot in folder name).
