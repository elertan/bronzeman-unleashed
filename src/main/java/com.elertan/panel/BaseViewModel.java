package com.elertan.panel;

import com.elertan.ui.Property;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for ViewModels that manages listener lifecycle.
 * Listeners registered via {@link #addListener} are automatically removed on {@link #close()}.
 */
public abstract class BaseViewModel implements AutoCloseable {

    private final List<ListenerBinding<?>> bindings = new ArrayList<>();

    /**
     * Registers a listener on a property and tracks it for cleanup.
     */
    protected <T> void addListener(Property<T> property, PropertyChangeListener listener) {
        property.addListener(listener);
        bindings.add(new ListenerBinding<>(property, listener));
    }

    @Override
    public void close() throws Exception {
        for (ListenerBinding<?> binding : bindings) {
            binding.remove();
        }
        bindings.clear();
    }

    private static final class ListenerBinding<T> {
        private final Property<T> property;
        private final PropertyChangeListener listener;

        ListenerBinding(Property<T> property, PropertyChangeListener listener) {
            this.property = property;
            this.listener = listener;
        }

        void remove() {
            property.removeListener(listener);
        }
    }
}
