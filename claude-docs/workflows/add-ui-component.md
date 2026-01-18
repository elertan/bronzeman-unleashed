# Adding a UI Component

Reusable UI components with MVVM pattern.

## Steps

### 1. Create ViewModel

Location: `src/main/java/com.elertan/panel/components/MyComponentViewModel.java`

```java
package com.elertan.panel.components;

import com.elertan.panel.BaseViewModel;
import com.elertan.ui.Property;
import lombok.Getter;

public class MyComponentViewModel extends BaseViewModel {

    @Getter
    private final Property<String> value = new Property<>("");

    @Getter
    private final Property<Boolean> enabled = new Property<>(true);

    // Event callback
    private Runnable onValueChanged;

    public void setOnValueChanged(Runnable callback) {
        this.onValueChanged = callback;
    }

    public void setValue(String newValue) {
        value.set(newValue);
        if (onValueChanged != null) {
            onValueChanged.run();
        }
    }
}
```

### 2. Create View

Location: `src/main/java/com.elertan/panel/components/MyComponent.java`

```java
package com.elertan.panel.components;

import com.elertan.ui.Bindings;
import net.runelite.client.ui.ColorScheme;
import javax.swing.*;
import java.awt.*;

public class MyComponent extends JPanel {

    private final MyComponentViewModel viewModel;
    private final JTextField textField;

    public MyComponent(MyComponentViewModel viewModel) {
        this.viewModel = viewModel;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Label
        JLabel label = new JLabel("Value:");
        add(label, BorderLayout.WEST);

        // Text field
        textField = new JTextField();
        textField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
            viewModel.setValue(textField.getText());
        }));
        add(textField, BorderLayout.CENTER);

        // Bind enabled state
        Bindings.bindEnabled(textField, viewModel.getEnabled());

        // Sync initial value
        viewModel.getValue().addListener(evt -> {
            if (!textField.getText().equals(viewModel.getValue().get())) {
                textField.setText(viewModel.getValue().get());
            }
        });
    }
}
```

### 3. Use in Screen

```java
// In a Screen
MyComponentViewModel componentVm = new MyComponentViewModel();
componentVm.setOnValueChanged(() -> {
    String value = componentVm.getValue().get();
    // Handle value change
});

MyComponent component = new MyComponent(componentVm);
add(component);
```

## Existing Components

| Component | ViewModel | Description |
|-----------|-----------|-------------|
| `GameRulesEditor` | `GameRulesEditorViewModel` | Toggle controls for game rules |

## Checklist

- [ ] ViewModel extends `BaseViewModel`
- [ ] View accepts ViewModel in constructor
- [ ] Properties bound to UI elements
- [ ] Callbacks for external event handling
- [ ] Uses RuneLite `ColorScheme` for styling
