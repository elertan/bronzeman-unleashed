package com.elertan.panel.components;

import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import java.awt.Component;
import javax.swing.JLabel;

public class ErrorLabel extends JLabel implements AutoCloseable {

    private final AutoCloseable visibleBinding;
    private final AutoCloseable textBinding;

    public ErrorLabel(Property<String> errorMessage) {
        setAlignmentX(Component.CENTER_ALIGNMENT);
        visibleBinding = Bindings.bindVisible(this,
            errorMessage.derive(msg -> msg != null && !msg.isEmpty()));
        textBinding = Bindings.bindLabelText(this,
            errorMessage.derive(msg -> msg == null || msg.isEmpty() ? ""
                : "<html><div style=\"text-align:center;color:red;\">" + msg + "</div></html>"));
    }

    @Override
    public void close() throws Exception {
        textBinding.close();
        visibleBinding.close();
    }
}
