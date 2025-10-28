package com.elertan.panel2.screens.main;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

import javax.swing.*;

public class UnlockedItemsScreen extends JPanel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        UnlockedItemsScreen create(UnlockedItemsScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public UnlockedItemsScreen create(UnlockedItemsScreenViewModel viewModel) {
            return new UnlockedItemsScreen(viewModel);
        }
    }

    private UnlockedItemsScreen(UnlockedItemsScreenViewModel viewModel) {

    }
}
