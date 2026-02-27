package com.elertan.panel.screens.setup.remoteStep;

import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

public class CheckingView extends JPanel implements AutoCloseable {

    private final Property<Boolean> isCancelled = new Property<>(false);
    private final AutoCloseable cancelButtonEnabledBinding;

    public CheckingView(Listener listener) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JLabel lbl = new JLabel("Checking configuration...");
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(lbl);
        center.add(Box.createVerticalStrut(10));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(bar);
        center.add(Box.createVerticalStrut(20));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton cancelButton = new JButton("Cancel");
        cancelButtonEnabledBinding = Bindings.bindEnabled(cancelButton, isCancelled.derive(b -> !b));
        cancelButton.addActionListener(e -> {
            isCancelled.set(true);
            listener.onCancel();
            isCancelled.set(false);
        });
        buttons.add(cancelButton);
        buttons.add(Box.createHorizontalGlue());
        center.add(buttons, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    @Override
    public void close() throws Exception {
        cancelButtonEnabledBinding.close();
    }

    public interface Listener {
        void onCancel();
    }
}
