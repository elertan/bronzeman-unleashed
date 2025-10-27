package com.elertan.ui;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Bindings {
    public static AutoCloseable bindEnabled(JComponent component, Property<Boolean> property) {
        Consumer<Boolean> valueConsumer = (Boolean value) -> {
            if (Objects.equals(component.isEnabled(), value)) {
                return;
            }
            component.setEnabled(value);
        };

        PropertyChangeListener listener = (event) -> invokeOnEDT(() -> {
            @SuppressWarnings("unchecked")
            Boolean newValue = (Boolean) event.getNewValue();
            valueConsumer.accept(newValue);
        });

        property.addListener(listener);
        valueConsumer.accept(property.get());

        return () -> property.removeListener(listener);
    }

    public static AutoCloseable bindTextFieldText(JTextField component, Property<String> property) {
        Consumer<String> valueConsumer = (String value) -> {
            String textValue = value == null ? "" : value;
            if (Objects.equals(component.getText(), textValue)) {
                return;
            }
            int caretPosition = component.getCaretPosition();
            component.setText(textValue);
            int newCaretPosition = Math.min(caretPosition, textValue.length());
            if (caretPosition == newCaretPosition) {
                return;
            }
            component.setCaretPosition(newCaretPosition);
        };

        PropertyChangeListener listener = (event) -> invokeOnEDT(() -> {
            String newValue = (String) event.getNewValue();
            valueConsumer.accept(newValue);
        });

        property.addListener(listener);
        valueConsumer.accept(property.get());

        return () -> property.removeListener(listener);
    }

    public static <E extends Enum<E>, P extends JPanel> AutoCloseable bindCardLayout(JPanel host, CardLayout cardLayout, Property<E> property, Function<E, P> build) {
        final Map<E, P> builtPanels = new HashMap<>();

        Consumer<E> valueConsumer = (E enumValue) -> {
            if (enumValue == null) {
                throw new IllegalArgumentException("property must have a non-null value");
            }

            String key = enumValue.name();

            if (!builtPanels.containsKey(enumValue)) {
                P panel = build.apply(enumValue);
                builtPanels.put(enumValue, panel);
                host.add(panel, key);
            }

            cardLayout.show(host, key);
        };

        PropertyChangeListener listener = (event) -> invokeOnEDT(() -> {
            @SuppressWarnings("unchecked")
            E newEnumValue = (E) event.getNewValue();
            valueConsumer.accept(newEnumValue);
        });

        property.addListener(listener);
        valueConsumer.accept(property.get());

        return () -> {
            property.removeListener(listener);

            // Close all built panels
//            for (P panel : builtPanels.values()) {
//                panel.close();
//            }
        };
    }

    private static void invokeOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
