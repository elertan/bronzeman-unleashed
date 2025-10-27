package com.elertan.ui;

import com.elertan.panel2.BUPanel2;
import com.elertan.panel2.BUPanelViewModel;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Bindings {
    public static void bindEnabled(JComponent component, Property<Boolean> property) {
        property.addListener((event) -> invokeOnEDT(() -> {
            Boolean newValue = (Boolean) event.getNewValue();
            if (Objects.equals(component.isEnabled(), newValue)) {
                return;
            }
            component.setEnabled(newValue);
        }));
    }

    public static void bindTextFieldText(JTextField component, Property<String> property) {
        property.addListener((event) -> invokeOnEDT(() -> {
            String newValue = (String) event.getNewValue();
            if (Objects.equals(component.getText(), newValue)) {
                return;
            }
            component.setText(newValue);
        }));
    }

    public static <T> void bindCardLayout(JPanel host, CardLayout cardLayout, Property<T> property, Function<T, JPanel> build) {
        Set<String> knownKeys = new HashSet<>();

        Consumer<T> valueConsumer = (T value) -> {
            String key = value.toString();

            if (!knownKeys.contains(key)) {
                knownKeys.add(key);
                host.add(build.apply(value), key);
            }

            cardLayout.show(host, key);
        };

        property.addListener((event) -> invokeOnEDT(() -> {
            @SuppressWarnings("unchecked")
            T newValue = (T) event.getNewValue();
            valueConsumer.accept(newValue);
        }));

        valueConsumer.accept(property.get());
    }

    private static void invokeOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
