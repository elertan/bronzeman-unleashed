package com.elertan.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class Property<T> {

    private static final Executor DEFAULT_EXECUTOR = Executors.newCachedThreadPool();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private volatile T value;

    public Property(T value) { this.value = value; }

    public T get() { return value; }

    public void set(T value) {
        T old = this.value;
        if (Objects.equals(old, value)) return;
        this.value = value;
        pcs.firePropertyChange("value", old, value);
    }

    public void addListener(PropertyChangeListener l) { pcs.addPropertyChangeListener(l); }
    public void removeListener(PropertyChangeListener l) { pcs.removePropertyChangeListener(l); }

    public <U> Property<U> derive(Function<T, U> transform) {
        Objects.requireNonNull(transform, "transform");
        Property<U> derived = new Property<>(transform.apply(this.value));
        this.addListener(evt -> {
            if ("value".equals(evt.getPropertyName())) {
                @SuppressWarnings("unchecked") T nv = (T) evt.getNewValue();
                derived.set(transform.apply(nv));
            }
        });
        return derived;
    }

    public <U> Property<U> deriveAsync(Function<T, U> transform) {
        return deriveAsync(transform, DEFAULT_EXECUTOR);
    }

    public <U> Property<U> deriveAsync(Function<T, U> transform, Executor executor) {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(executor, "executor");
        Property<U> derived = new Property<>(null);
        executor.execute(() -> derived.set(transform.apply(this.value)));
        this.addListener(evt -> {
            if ("value".equals(evt.getPropertyName())) {
                @SuppressWarnings("unchecked") T nv = (T) evt.getNewValue();
                executor.execute(() -> derived.set(transform.apply(nv)));
            }
        });
        return derived;
    }

    public static <R> Property<R> deriveMany(List<? extends Property<?>> properties, Function<List<?>, R> combiner) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(combiner, "combiner");
        if (properties.contains(null)) throw new NullPointerException("properties contains null");

        Property<R> derived = new Property<>(combiner.apply(collectValues(properties)));
        PropertyChangeListener listener = evt -> {
            if ("value".equals(evt.getPropertyName())) {
                derived.set(combiner.apply(collectValues(properties)));
            }
        };
        for (Property<?> p : properties) p.addListener(listener);
        return derived;
    }

    public static <R> Property<R> deriveManyAsync(List<? extends Property<?>> properties, Function<List<?>, R> combiner) {
        return deriveManyAsync(properties, combiner, DEFAULT_EXECUTOR);
    }

    public static <R> Property<R> deriveManyAsync(List<? extends Property<?>> properties, Function<List<?>, R> combiner, Executor executor) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(combiner, "combiner");
        Objects.requireNonNull(executor, "executor");
        if (properties.contains(null)) throw new NullPointerException("properties contains null");

        Property<R> derived = new Property<>(null);
        Runnable recompute = () -> derived.set(combiner.apply(collectValues(properties)));
        executor.execute(recompute);
        PropertyChangeListener listener = evt -> {
            if ("value".equals(evt.getPropertyName())) executor.execute(recompute);
        };
        for (Property<?> p : properties) p.addListener(listener);
        return derived;
    }

    private static List<?> collectValues(List<? extends Property<?>> properties) {
        List<Object> vals = new ArrayList<>(properties.size());
        for (Property<?> p : properties) vals.add(p.get());
        return Collections.unmodifiableList(vals);
    }
}
