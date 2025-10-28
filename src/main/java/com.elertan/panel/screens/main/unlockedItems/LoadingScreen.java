package com.elertan.panel.screens.main.unlockedItems;

import com.elertan.BUResourceService;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.swing.*;
import java.awt.*;

public class LoadingScreen extends JPanel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        LoadingScreen create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject
        private BUResourceService buResourceService;

        @Override
        public LoadingScreen create() {
            return new LoadingScreen(buResourceService);
        }
    }

    private LoadingScreen(BUResourceService buResourceService) {
        setLayout(new BorderLayout());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        JLabel titleLabel = new JLabel("Bronzeman Unleashed");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(titleLabel);

        JLabel subtitleLabel = new JLabel();
        subtitleLabel.setText("Loading...");
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
