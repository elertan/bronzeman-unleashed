# Overlays

In-game overlay for item unlock notifications.

## Files

| File | Description |
|------|-------------|
| `overlays/ItemUnlockOverlay.java` | Unlock notification overlay |

## ItemUnlockOverlay

Displays unlock animation when player obtains new item.

Managed by `BUOverlayService`.

### Features
- Shows item icon
- Displays "UNLOCKED" text
- Animation effect
- Auto-dismisses after duration

## BUOverlayService

Registers overlays with RuneLite:

```java
@Inject private OverlayManager overlayManager;
@Inject private ItemUnlockOverlay itemUnlockOverlay;

public void startUp() {
    overlayManager.add(itemUnlockOverlay);
}

public void shutDown() {
    overlayManager.remove(itemUnlockOverlay);
}
```
