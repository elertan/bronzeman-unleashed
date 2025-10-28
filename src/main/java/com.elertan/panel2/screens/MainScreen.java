package com.elertan.panel2.screens;

import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

import javax.swing.*;
import java.awt.*;

public class MainScreen extends JPanel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainScreen create(MainScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public MainScreen create(MainScreenViewModel viewModel) {
            return new MainScreen(viewModel);
        }
    }

    private final AutoCloseable cardLayoutBinding;

    private MainScreen(MainScreenViewModel viewModel) {
        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);

        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.mainScreen, this::buildScreen);
    }

    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
    }


    private JPanel buildScreen(MainScreenViewModel.MainScreen screen) {
        switch (screen) {
            case UNLOCKED_ITEMS:
                return new JPanel();
            case CONFIG:
                return new JPanel();
        }

        throw new IllegalStateException("Unknown main screen: " + screen);
    }
}
