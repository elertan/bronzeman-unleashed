package com.elertan.panel.components;

import com.elertan.BUResourceService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class LoadingPanel extends JPanel {

    public LoadingPanel(BUResourceService buResourceService, String message) {
        setLayout(new BorderLayout());
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        JLabel titleLabel = new JLabel("Bronzeman Unleashed");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(titleLabel);

        JLabel subtitleLabel = new JLabel(message);
        subtitleLabel.setBorder(BorderFactory.createEmptyBorder(20, 15, 0, 15));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(subtitleLabel);
        inner.add(Box.createVerticalStrut(15));

        JLabel spinnerLabel = new JLabel();
        spinnerLabel.setIcon(buResourceService.getLoadingSpinnerImageIcon());
        spinnerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(spinnerLabel);

        add(inner, BorderLayout.NORTH);
    }
}
