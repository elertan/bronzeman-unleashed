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
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import net.runelite.client.ui.components.IconTextField;

public final class Bindings {

    public interface TextComponent {
        String getText();
        void setText(String value);
        Document getDocument();
    }

    public static AutoCloseable bindEnabled(JComponent component, Property<Boolean> property) {
        return bind(property, component::isEnabled, component::setEnabled);
    }

    public static AutoCloseable bindVisible(JComponent component, Property<Boolean> property) {
        return bind(property, component::isVisible, component::setVisible);
    }

    public static AutoCloseable bindSelected(JCheckBox component, Property<Boolean> property) {
        ChangeListener listener = e -> property.set(component.isSelected());
        component.addChangeListener(listener);
        component.setSelected(property.get());
        AutoCloseable binding = bind(property, component::isSelected, component::setSelected);
        return () -> {
            binding.close();
            component.removeChangeListener(listener);
        };
    }

    public static AutoCloseable bindLabelText(JLabel component, Property<String> property) {
        return bind(property, component::getText, v -> component.setText(v == null ? "" : v));
    }

    public static AutoCloseable bindSpinner(JSpinner component, Property<Integer> property) {
        Supplier<Integer> getter = () -> ((Number) component.getValue()).intValue();
        Consumer<Integer> setter = v -> component.setValue(v == null ? 0 : v);
        ChangeListener listener = e -> property.set(getter.get());
        component.addChangeListener(listener);
        AutoCloseable binding = bind(property, getter, setter);
        return () -> {
            binding.close();
            component.removeChangeListener(listener);
        };
    }

    public static AutoCloseable bindTextComponentText(TextComponent tc, Property<String> property) {
        DocumentListener docListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { property.set(tc.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { property.set(tc.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { property.set(tc.getText()); }
        };
        Document document = tc.getDocument();
        document.addDocumentListener(docListener);
        @SuppressWarnings("resource")
        AutoCloseable binding = bind(property, tc::getText, v -> tc.setText(v == null ? "" : v));
        return () -> {
            binding.close();
            document.removeDocumentListener(docListener);
        };
    }

    public static AutoCloseable bindTextFieldText(JTextField component, Property<String> property) {
        return bindTextComponentText(new TextComponent() {
            @Override public String getText() { return component.getText(); }
            @Override public void setText(String value) {
                String text = value == null ? "" : value;
                int caret = component.getCaretPosition();
                component.setText(text);
                int newCaret = Math.min(caret, text.length());
                if (caret != newCaret) component.setCaretPosition(newCaret);
            }
            @Override public Document getDocument() { return component.getDocument(); }
        }, property);
    }

    public static AutoCloseable bindIconTextFieldText(IconTextField component, Property<String> property) {
        return bindTextComponentText(new TextComponent() {
            @Override public String getText() { return component.getText(); }
            @Override public void setText(String value) { component.setText(value == null ? "" : value); }
            @Override public Document getDocument() { return component.getDocument(); }
        }, property);
    }

    public static <T> AutoCloseable bindComboBox(
        JComboBox<T> comboBox, Property<List<T>> optionsProperty,
        Property<T> valueProperty, Property<Map<T, String>> valueToStringMapProperty
    ) {
        Objects.requireNonNull(comboBox, "comboBox must not be null");
        Objects.requireNonNull(valueProperty, "valueProperty must not be null");
        Objects.requireNonNull(valueToStringMapProperty, "valueToStringMapProperty must not be null");

        AtomicReference<Boolean> isUpdatingOptions = new AtomicReference<>(false);

        Supplier<List<T>> optionsGetter = () -> {
            List<T> options = new ArrayList<>(comboBox.getItemCount());
            for (int i = 0; i < comboBox.getItemCount(); i++) options.add(comboBox.getItemAt(i));
            return options;
        };

        Consumer<List<T>> optionsSetter = options -> {
            isUpdatingOptions.set(true);
            try {
                DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
                if (options != null) for (T o : options) model.addElement(o);
                comboBox.setModel(model);
                comboBox.setSelectedItem(valueProperty.get());
            } finally {
                isUpdatingOptions.set(false);
            }
        };

        Supplier<T> valueGetter = () -> {
            @SuppressWarnings("unchecked") T selected = (T) comboBox.getSelectedItem();
            return selected;
        };

        ListCellRenderer<? super T> renderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus
            ) {
                @SuppressWarnings("unchecked") T typed = (T) value;
                String text = valueToStringMapProperty.get().getOrDefault(typed, "Unknown");
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        };
        comboBox.setRenderer(renderer);

        ActionListener listener = e -> {
            if (Boolean.TRUE.equals(isUpdatingOptions.get())) return;
            @SuppressWarnings("unchecked") T selected = (T) comboBox.getSelectedItem();
            valueProperty.set(selected);
        };
        comboBox.addActionListener(listener);

        @SuppressWarnings("resource")
        AutoCloseable optionsBinding = bind(optionsProperty, optionsGetter, optionsSetter);
        @SuppressWarnings("resource")
        AutoCloseable valueBinding = bind(valueProperty, valueGetter, comboBox::setSelectedItem);

        PropertyChangeListener mapListener = evt -> comboBox.repaint();
        valueToStringMapProperty.addListener(mapListener);

        return () -> {
            valueToStringMapProperty.removeListener(mapListener);
            valueBinding.close();
            optionsBinding.close();
            comboBox.removeActionListener(listener);
        };
    }

    public static <E extends Enum<E>, P extends JPanel> AutoCloseable bindCardLayout(
        JPanel host, CardLayout cardLayout, Property<E> property, Function<E, P> build
    ) {
        AtomicReference<E> lastEnum = new AtomicReference<>(null);
        Map<E, P> builtPanels = new HashMap<>();

        Consumer<E> setter = enumValue -> {
            if (enumValue == null) throw new IllegalArgumentException("property must have a non-null value");
            String key = enumValue.name();
            if (!builtPanels.containsKey(enumValue)) {
                P panel = build.apply(enumValue);
                builtPanels.put(enumValue, panel);
                host.add(panel, key);
            }
            lastEnum.set(enumValue);
            cardLayout.show(host, key);
            host.revalidate();
            host.repaint();
        };

        return bind(property, lastEnum::get, setter);
    }

    public static <T> AutoCloseable bind(Property<T> property, Supplier<T> getter, Consumer<T> setter) {
        PropertyChangeListener listener = event -> invokeOnEDT(() -> {
            @SuppressWarnings("unchecked") T newValue = (T) event.getNewValue();
            if (Objects.equals(getter.get(), newValue)) return;
            setter.accept(newValue);
        });
        property.addListener(listener);
        setter.accept(property.get());
        return () -> property.removeListener(listener);
    }

    public static void invokeOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) runnable.run();
        else SwingUtilities.invokeLater(runnable);
    }
}
