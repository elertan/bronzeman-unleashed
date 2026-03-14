# Running the Plugin

## Prerequisites

- Java 8+ JDK
- Gradle (wrapper included)
- RuneLite developer setup

## Build

```bash
./gradlew build
```

Output: `build/libs/bronzeman-unleashed-0.2.0.jar`

## Run with RuneLite

### Option 1: Plugin Hub (Production)

Search "Bronzeman Unleashed" in RuneLite Plugin Hub.

### Option 2: Development Mode

1. Clone RuneLite repository
2. Add plugin to RuneLite's `runelite-client/pom.xml` or use external plugins directory
3. Run RuneLite with plugin loaded

### Option 3: External Plugin

1. Build the plugin JAR
2. Copy to RuneLite external plugins directory:
   - Windows: `%USERPROFILE%\.runelite\plugins`
   - macOS: `~/.runelite/plugins`
   - Linux: `~/.runelite/plugins`
3. Enable "External Plugins" in RuneLite settings
4. Restart RuneLite

## First-Time Setup

1. Open plugin panel (click icon in sidebar)
2. Enter Firebase Realtime Database URL
3. Configure game rules
4. Plugin tracks unlocks automatically

## Firebase Setup

See [firebase-guide.md](../../firebase-guide.md) for Firebase configuration.

## Logs

Enable debug logging in RuneLite:
1. Settings → "Developer"
2. Enable "Debug logging"
3. Check logs at `~/.runelite/logs/`

Plugin logs prefix: `BU:`

## Tests

```bash
./gradlew test
```

Test file: `src/test/java/com/elertan/BronzemanUnleashedPluginTest.java`
