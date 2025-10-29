package com.elertan.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import net.runelite.client.ui.components.IconTextField;

public final class Bindings {

    public static AutoCloseable bindEnabled(JComponent component, Property<Boolean> property) {
        Supplier<Boolean> getter = component::isEnabled;
        Consumer<Boolean> setter = component::setEnabled;

        return bind(property, getter, setter);
    }

    public static AutoCloseable bindVisible(JComponent component, Property<Boolean> property) {
        Supplier<Boolean> getter = component::isVisible;
        Consumer<Boolean> setter = component::setVisible;

        return bind(property, getter, setter);
    }

    public static AutoCloseable bindSelected(AbstractButton component, Property<Boolean> property) {
        Supplier<Boolean> getter = component::isSelected;
        Consumer<Boolean> setter = component::setSelected;

        return bind(property, getter, setter);
    }

    public static AutoCloseable bindLabelText(JLabel component, Property<String> property) {
        Supplier<String> getter = component::getText;
        Consumer<String> setter = (String value) -> {
            String textValue = value == null ? "" : value;
            component.setText(textValue);
        };

        return bind(property, getter, setter);
    }

    public static AutoCloseable bindSpinner(JSpinner component, Property<Integer> property) {
        Supplier<Integer> getter = () -> {
            Number number = (Number) component.getValue();
            return number.intValue();
        };
        Consumer<Integer> setter = value -> {
            int intValue = value == null ? 0 : value;
            component.setValue(intValue);
        };

        ChangeListener listener = (ChangeEvent e) -> {
            property.set(getter.get());
        };
        component.addChangeListener(listener);

        AutoCloseable binding = bind(property, getter, setter);

        return () -> {
            binding.close();
            component.removeChangeListener(listener);
        };
    }

    public static AutoCloseable bindTextFieldText(JTextField component, Property<String> property) {
        Supplier<String> getter = component::getText;

        Consumer<String> setter = (String value) -> {
            String textValue = value == null ? "" : value;
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
        AutoCloseable binding = bind(property, getter, setter);

        return () -> {
            binding.close();
            document.removeDocumentListener(documentListener);
        };
    }

    public static AutoCloseable bindIconTextFieldText(IconTextField component,
        Property<String> property) {
        Supplier<String> getter = component::getText;

        Consumer<String> setter = (String value) -> {
            String textValue = value == null ? "" : value;
            component.setText(textValue);
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
        AutoCloseable binding = bind(property, getter, setter);

        return () -> {
            binding.close();
            document.removeDocumentListener(documentListener);
        };
    }

    public static <T> AutoCloseable bindComboBox(
        JComboBox<T> comboBox,
        Property<List<T>> optionsProperty,
        Property<T> valueProperty,
        Property<Map<T, String>> valueToStringMapProperty
    ) {
        if (comboBox == null) {
            throw new IllegalArgumentException("comboBox must not be null");
        }
        if (valueProperty == null) {
            throw new IllegalArgumentException("valueProperty must not be null");
        }
        if (valueToStringMapProperty == null) {
            throw new IllegalArgumentException("valueToStringMapProperty must not be null");
        }

        AtomicReference<Boolean> isUpdatingOptions = new AtomicReference<>(false);

        Supplier<List<T>> optionsGetter = () -> {
            int itemCount = comboBox.getItemCount();
            List<T> options = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                @SuppressWarnings("unchecked")
                T item = comboBox.getItemAt(i);
                options.add(item);
            }
            return options;
        };

        Consumer<List<T>> optionsSetter = (List<T> options) -> {
            isUpdatingOptions.set(true);
            try {
                DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
                if (options != null) {
                    for (T o : options) {
                        model.addElement(o);
                    }
                }
                comboBox.setModel(model); // selects first internally
                comboBox.setSelectedItem(valueProperty.get()); // restore intended value
            } finally {
                isUpdatingOptions.set(false);
            }
        };

        Supplier<T> valueGetter = () -> {
            @SuppressWarnings("unchecked")
            T selected = (T) comboBox.getSelectedItem();
            return selected;
        };

        Consumer<T> valueSetter = comboBox::setSelectedItem;

        // Use provided mapping for display text, defaulting to enum name
        ListCellRenderer<? super T> renderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                @SuppressWarnings("unchecked")
                T typedValue = (T) value;
                Map<T, String> valueToStringMap = valueToStringMapProperty.get();
                String text = valueToStringMap.getOrDefault(typedValue, "null");
                return super.getListCellRendererComponent(
                    list,
                    text,
                    index,
                    isSelected,
                    cellHasFocus
                );
            }
        };
        comboBox.setRenderer(renderer);

        ActionListener listener = e -> {
            Boolean isUpdatingOptionsValue = isUpdatingOptions.get();
            if (isUpdatingOptionsValue != null && isUpdatingOptionsValue) {
                return;
            }

            @SuppressWarnings("unchecked")
            T selected = (T) comboBox.getSelectedItem();
            valueProperty.set(selected);
        };
        comboBox.addActionListener(listener);

        @SuppressWarnings("resource")
        AutoCloseable optionsBinding = bind(optionsProperty, optionsGetter, optionsSetter);
        @SuppressWarnings("resource")
        AutoCloseable valueBinding = bind(valueProperty, valueGetter, valueSetter);

        PropertyChangeListener valueToStringMapPropertyListener = evt -> comboBox.repaint();
        valueToStringMapProperty.addListener(valueToStringMapPropertyListener);

        return () -> {
            valueToStringMapProperty.removeListener(valueToStringMapPropertyListener);
            valueBinding.close();
            optionsBinding.close();
            comboBox.removeActionListener(listener);
        };
    }

    public static <E extends Enum<E>, P extends JPanel> AutoCloseable bindCardLayout(
        JPanel host,
        CardLayout cardLayout,
        Property<E> property,
        Function<E, P> build
    ) {
        AtomicReference<E> lastEnum = new AtomicReference<>(null);
        final Map<E, P> builtPanels = new HashMap<>();

        Supplier<E> getter = lastEnum::get;

        Consumer<E> setter = (E enumValue) -> {
            if (enumValue == null) {
                throw new IllegalArgumentException("property must have a non-null value");
            }

            String key = enumValue.name();

            if (!builtPanels.containsKey(enumValue)) {
                P panel = build.apply(enumValue);
                builtPanels.put(enumValue, panel);
                host.add(panel, key);
            }

            lastEnum.set(enumValue);
            cardLayout.show(host, key);
        };

        return bind(property, getter, setter);
    }

    public static <T> AutoCloseable bind(Property<T> property, Supplier<T> getter,
        Consumer<T> setter) {
        PropertyChangeListener listener = (event) -> invokeOnEDT(() -> {
            @SuppressWarnings("unchecked")
            T newValue = (T) event.getNewValue();

            T oldValue = getter.get();
            if (Objects.equals(oldValue, newValue)) {
                return;
            }
            setter.accept(newValue);
        });

        property.addListener(listener);
        setter.accept(property.get());

        return () -> property.removeListener(listener);
    }

    public static void invokeOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
