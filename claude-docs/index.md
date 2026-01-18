# Bronzeman Unleashed - Developer Documentation

RuneLite plugin for "Bronzeman mode" - unlock items before trading them. Supports solo and group play via Firebase sync.

## Quick Links

- [Architecture Overview](overview.md)
- [Conventions & Patterns](conventions.md)

## Categories

### Core
- [Plugin Core](core.md) - BUPlugin, config, lifecycle

### Domain
- [Services](services.md) - Business logic services
- [Policies](policies.md) - Game rule enforcement
- [Events](events.md) - Event system for achievements/drops
- [Models](models.md) - Data structures

### Data Layer
- [Data Providers](data-providers.md) - State management with Firebase sync
- [Remote Storage](remote.md) - Firebase Realtime Database integration

### UI
- [Panel System](panel.md) - Side panel UI (MVVM pattern)
- [Overlays](overlays.md) - In-game overlays

### Infrastructure
- [Chat Parsing](chat.md) - Game message parsing
- [Utilities](utils.md) - Helper classes

## Workflows

### Setup & Configuration
- [Adding a New Service](workflows/add-service.md)
- [Adding a New Policy](workflows/add-policy.md)
- [Adding a New Event Type](workflows/add-event.md)
- [Adding a Data Provider](workflows/add-data-provider.md)

### UI Development
- [Adding a Panel Screen](workflows/add-panel-screen.md)
- [Adding a UI Component](workflows/add-ui-component.md)

### Storage
- [Adding Firebase Storage](workflows/add-firebase-storage.md)

### Testing & Debug
- [Running the Plugin](workflows/run-plugin.md)
- [Debug Commands](workflows/debug-commands.md)
