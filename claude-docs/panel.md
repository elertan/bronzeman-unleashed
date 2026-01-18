# Panel System

Side panel UI using MVVM pattern with Swing.

## Architecture

```
BUPanelService (manages lifecycle)
    ↓
BUPanel (root container)
    ↓
Screens (SetupScreen, MainScreen, WaitForLoginScreen)
    ↓
Views + ViewModels
```

## Core Files

| File | Description |
|------|-------------|
| `panel/BUPanel.java` | Root panel container |
| `panel/BUPanelViewModel.java` | Root view model |
| `panel/BaseViewModel.java` | Base class for view models |
| `panel/ViewportWidthTrackingPanel.java` | Panel that tracks viewport width |

## UI Framework

| File | Description |
|------|-------------|
| `ui/Property.java` | Observable property with listeners |
| `ui/Bindings.java` | UI binding utilities |

### Property

```java
Property<String> text = new Property<>("initial");
text.addListener(evt -> updateUI());
text.set("new value");  // Triggers listeners
String value = text.get();
```

### Bindings

```java
Bindings.bindText(label, viewModel.getText());
Bindings.bindVisible(component, viewModel.getIsVisible());
```

## Screens

### Setup Flow
```
SetupScreen
├── RemoteStepView (Firebase URL entry)
│   ├── EntryView (URL input)
│   └── CheckingView (validation)
└── GameRulesStepView (rules configuration)
```

### Main Screen
```
MainScreen
├── UnlockedItemsScreen (item list with search/filter)
│   ├── HeaderView (search, sort controls)
│   ├── LoadingScreen
│   └── ItemsScreen (grid of items)
│       └── MainView (individual item display)
└── ConfigScreen (settings)
```

## Screen Files

| Screen | View | ViewModel |
|--------|------|-----------|
| Setup | `screens/SetupScreen.java` | `screens/SetupScreenViewModel.java` |
| Remote Step | `screens/setup/RemoteStepView.java` | `screens/setup/RemoteStepViewViewModel.java` |
| Entry | `screens/setup/remoteStep/EntryView.java` | `screens/setup/remoteStep/EntryViewViewModel.java` |
| Checking | `screens/setup/remoteStep/CheckingView.java` | `screens/setup/remoteStep/CheckingViewViewModel.java` |
| Game Rules Step | `screens/setup/GameRulesStepView.java` | `screens/setup/GameRulesStepViewViewModel.java` |
| Main | `screens/MainScreen.java` | `screens/MainScreenViewModel.java` |
| Unlocked Items | `screens/main/UnlockedItemsScreen.java` | `screens/main/UnlockedItemsScreenViewModel.java` |
| Config | `screens/main/ConfigScreen.java` | `screens/main/ConfigScreenViewModel.java` |
| Wait For Login | `screens/WaitForLoginScreen.java` | - |

## Components

| Component | View | ViewModel |
|-----------|------|-----------|
| Game Rules Editor | `components/GameRulesEditor.java` | `components/GameRulesEditorViewModel.java` |

## MVVM Pattern

### View
- Swing JPanel subclass
- Takes ViewModel in constructor
- Binds UI elements to ViewModel properties
- Calls ViewModel methods on user interaction

### ViewModel
- Extends `BaseViewModel`
- Exposes `Property<T>` fields for UI binding
- Contains business logic
- `close()` cleans up listeners automatically

```java
// ViewModel
public class MyViewModel extends BaseViewModel {
    private final Property<String> text = new Property<>("");
    public Property<String> getText() { return text; }
    public void onButtonClick() { text.set("clicked"); }
}

// View
public class MyView extends JPanel {
    public MyView(MyViewModel vm) {
        JLabel label = new JLabel();
        Bindings.bindText(label, vm.getText());
        JButton btn = new JButton("Click");
        btn.addActionListener(e -> vm.onButtonClick());
    }
}
```
