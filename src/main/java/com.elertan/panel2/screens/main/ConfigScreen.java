package com.elertan.panel2.screens.main;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

import javax.swing.*;

public class ConfigScreen extends JPanel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        ConfigScreen create(ConfigScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public ConfigScreen create(ConfigScreenViewModel viewModel) {
            return new ConfigScreen(viewModel);
        }
    }

    private ConfigScreen(ConfigScreenViewModel viewModel) {

    }
}
