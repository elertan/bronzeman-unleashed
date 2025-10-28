package com.elertan.panel.screens.main;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

public class ConfigScreenViewModel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        ConfigScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public ConfigScreenViewModel create() {
            return new ConfigScreenViewModel();
        }
    }

    private ConfigScreenViewModel() {
    }
}
