package com.elertan.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.function.Function;

public final class Property<T> {
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private volatile T value;

    public Property(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        T oldValue = this.value;
        if (Objects.equals(oldValue, value)) {
            return;
        }
        this.value = value;
        propertyChangeSupport.firePropertyChange("value", oldValue, value);
    }

    public void addListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public <U> Property<U> derive(Function<T, U> transform) {
        Objects.requireNonNull(transform, "transform");
        // Initialize derived property with the transformed current value
        Property<U> derived = new Property<>(transform.apply(this.value));

        // Update the derived property whenever this property's value changes
        PropertyChangeListener listener = evt -> {
            if ("value".equals(evt.getPropertyName())) {
                @SuppressWarnings("unchecked")
                T newValue = (T) evt.getNewValue();
                derived.set(transform.apply(newValue));
            }
        };
        this.addListener(listener);

        return derived;
    }
}
