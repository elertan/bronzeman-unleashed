package com.elertan.panel;

import com.elertan.BUResourceService;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;

@Slf4j
public class WaitForLoginPanel extends JPanel {
    public WaitForLoginPanel(BUResourceService buResourceService) {
        setLayout(new BorderLayout());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        JLabel titleLabel = new JLabel("Bronzeman Unleashed");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(titleLabel);

        JLabel subtitleLabel = new JLabel();
        subtitleLabel.setText("Waiting for account login...");
        subtitleLabel.setBorder(BorderFactory.createEmptyBorder(20, 15, 0, 15));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(subtitleLabel);

        inner.add(Box.createVerticalStrut(15));

        JLabel loadingSpinnerLabel = new JLabel();
        loadingSpinnerLabel.setIcon(buResourceService.getLoadingSpinnerImageIcon());
        loadingSpinnerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(loadingSpinnerLabel);

        add(inner, BorderLayout.NORTH);
    }
}
