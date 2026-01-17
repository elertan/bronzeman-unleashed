# Adding a Panel Screen

Panel screens use MVVM pattern with Swing.

## Steps

### 1. Create ViewModel

Location: `src/main/java/com.elertan/panel/screens/MyScreenViewModel.java`

```java
package com.elertan.panel.screens;

import com.elertan.panel.BaseViewModel;
import com.elertan.ui.Property;
import lombok.Getter;

public class MyScreenViewModel extends BaseViewModel {

    @Getter
    private final Property<String> title = new Property<>("My Screen");

    @Getter
    private final Property<Boolean> loading = new Property<>(false);

    // Dependencies injected or passed via constructor
    public MyScreenViewModel() {
        // Initialize
    }

    // Actions
    public void onButtonClick() {
        loading.set(true);
        // Do work
        loading.set(false);
    }

    @Override
    public void close() throws Exception {
        // Custom cleanup before base cleanup
        super.close();
    }
}
```

### 2. Create View

Location: `src/main/java/com.elertan/panel/screens/MyScreen.java`

```java
package com.elertan.panel.screens;

import com.elertan.ui.Bindings;
import net.runelite.client.ui.ColorScheme;
import javax.swing.*;
import java.awt.*;

public class MyScreen extends JPanel {

    private final MyScreenViewModel viewModel;

    public MyScreen(MyScreenViewModel viewModel) {
        this.viewModel = viewModel;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Title
        JLabel titleLabel = new JLabel();
        Bindings.bindText(titleLabel, viewModel.getTitle());
        add(titleLabel, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel();
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        add(content, BorderLayout.CENTER);

        // Button
        JButton button = new JButton("Click Me");
        button.addActionListener(e -> viewModel.onButtonClick());
        add(button, BorderLayout.SOUTH);

        // Conditional visibility
        JLabel loadingLabel = new JLabel("Loading...");
        Bindings.bindVisible(loadingLabel, viewModel.getLoading());
        content.add(loadingLabel);
    }
}
```

### 3. Integrate into Navigation

Edit parent screen/panel to show your screen:

```java
// In MainScreen or BUPanel
MyScreenViewModel myVm = new MyScreenViewModel();
MyScreen myScreen = new MyScreen(myVm);

// Add to card layout or replace content
cardPanel.add(myScreen, "myScreen");
cardLayout.show(cardPanel, "myScreen");
```

### 4. Handle Cleanup

Ensure ViewModel is closed when screen is removed:

```java
// When switching away or shutting down
myVm.close();
```

## Bindings API

| Method | Description |
|--------|-------------|
| `Bindings.bindText(JLabel, Property<String>)` | Bind label text |
| `Bindings.bindVisible(JComponent, Property<Boolean>)` | Bind visibility |
| `Bindings.bindEnabled(JComponent, Property<Boolean>)` | Bind enabled state |

## Checklist

- [ ] ViewModel extends `BaseViewModel`
- [ ] Properties use `Property<T>` for observability
- [ ] View binds to ViewModel properties
- [ ] Actions call ViewModel methods
- [ ] ViewModel closed on screen removal
- [ ] Uses `ColorScheme` for consistent styling
