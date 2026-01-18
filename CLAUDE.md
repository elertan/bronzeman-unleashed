# Bronzeman Unleashed

RuneLite plugin for "Bronzeman mode" - unlock items before trading them. Play solo or sync with group via Firebase.

## IMPORTANT: Check Documentation First

**BEFORE exploring the codebase for any task, ALWAYS check [claude-docs/](claude-docs/) first.**

Common tasks have documented workflows in [claude-docs/workflows/](claude-docs/workflows/):
- [add-service.md](claude-docs/workflows/add-service.md) - Adding new services
- [add-policy.md](claude-docs/workflows/add-policy.md) - Adding game rule enforcement
- [add-event.md](claude-docs/workflows/add-event.md) - Adding event types

→ **See [claude-docs/workflows/](claude-docs/workflows/) for all workflows**

**Only explore the codebase if:**
1. No relevant workflow exists in claude-docs/
2. You need to find a specific reference implementation mentioned in the docs
3. The task is truly unique and not covered

## Tech Stack

- **Language**: Java 8
- **Framework**: RuneLite Client API
- **Build**: Gradle
- **Annotations**: Lombok
- **Remote Storage**: Firebase Realtime Database (SSE)
- **Serialization**: Gson

## Project Structure

```
bronzeman-unleashed/
├── claude-docs/           # PROJECT DOCUMENTATION - CHECK FIRST
├── src/main/java/com.elertan/
│   ├── BUPlugin.java      # Entry point
│   ├── *Service.java      # Domain services
│   ├── policies/          # Game rule enforcement
│   ├── data/              # Data providers (Firebase sync)
│   ├── remote/firebase/   # Firebase adapters
│   ├── panel/             # UI (MVVM)
│   ├── event/             # Event system
│   ├── chat/              # Chat parsing
│   ├── models/            # Data models
│   └── utils/             # Utilities
└── build.gradle           # Build config
```

## Key Conventions

- All services/policies implement `BUPluginLifecycle` with `startUp()`/`shutDown()`
- Policies extend `PolicyBase` and use `createContext().shouldApplyForRules()`
- Data providers extend `AbstractDataProvider` for Firebase sync
- Panel uses MVVM: Views bind to ViewModels extending `BaseViewModel`
- Storage uses port/adapter pattern: interfaces in `remote/`, Firebase impls in `remote/firebase/`

## Quick Reference

- [Workflows](claude-docs/workflows/) ← Start here for any task
- [Architecture Overview](claude-docs/overview.md)
- [Conventions](claude-docs/conventions.md)
- [Services](claude-docs/services.md)
- [Policies](claude-docs/policies.md)
- [Data Providers](claude-docs/data-providers.md)
- [Panel UI](claude-docs/panel.md)

---
*Indexed: 2025-12-29 | Hash: a1cd0aefda59be2b47dcf9309f2c8ebb41b7f578*
