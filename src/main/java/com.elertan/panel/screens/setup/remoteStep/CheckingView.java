package com.elertan.panel.screens.setup.remoteStep;

import com.elertan.ui.Bindings;

import javax.swing.*;
import java.awt.*;

public class CheckingView extends JPanel implements AutoCloseable {
    private final AutoCloseable cancelButtonEnabledBinding;

    public CheckingView(CheckingViewViewModel viewModel) {
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
        cancelButtonEnabledBinding = Bindings.bindEnabled(cancelButton, viewModel.isCancelled.derive(b -> !b));
        cancelButton.addActionListener(e -> viewModel.onCancelButtonClick());
        buttons.add(cancelButton);

        buttons.add(Box.createHorizontalGlue());

        center.add(buttons, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    @Override
    public void close() throws Exception {
        cancelButtonEnabledBinding.close();
    }
}
