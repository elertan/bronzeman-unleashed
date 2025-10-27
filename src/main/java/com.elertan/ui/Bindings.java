package com.elertan.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
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

        return bind(property, valueConsumer);
    }

    public static AutoCloseable bindVisible(JComponent component, Property<Boolean> property) {
        Consumer<Boolean> valueConsumer = (Boolean value) -> {
            if (Objects.equals(component.isVisible(), value)) {
                return;
            }
            component.setVisible(value);
        };

        return bind(property, valueConsumer);
    }

    public static AutoCloseable bindSelected(AbstractButton component, Property<Boolean> property) {
        Consumer<Boolean> valueConsumer = (Boolean value) -> {
            if (Objects.equals(component.isSelected(), value)) {
                return;
            }
            component.setSelected(value);
        };

        return bind(property, valueConsumer);
    }

    public static AutoCloseable bindLabelText(JLabel component, Property<String> property) {
        Consumer<String> valueConsumer = (String value) -> {
            String textValue = value == null ? "" : value;
            if (Objects.equals(component.getText(), textValue)) {
                return;
            }
            component.setText(textValue);
        };

        return bind(property, valueConsumer);
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

        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                property.set(component.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                property.set(component.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                property.set(component.getText());
            }
        };

        Document document = component.getDocument();
        document.addDocumentListener(documentListener);

        @SuppressWarnings("resource")
        AutoCloseable binding = bind(property, valueConsumer);

        return () -> {
            binding.close();
            document.removeDocumentListener(documentListener);
        };
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

        return bind(property, valueConsumer);
    }

    public static <T> AutoCloseable bind(Property<T> property, Consumer<T> valueConsumer) {
        PropertyChangeListener listener = (event) -> invokeOnEDT(() -> {
            @SuppressWarnings("unchecked")
            T newValue = (T) event.getNewValue();
            valueConsumer.accept(newValue);
        });

        property.addListener(listener);
        valueConsumer.accept(property.get());

        return () -> property.removeListener(listener);
    }

    private static void invokeOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
