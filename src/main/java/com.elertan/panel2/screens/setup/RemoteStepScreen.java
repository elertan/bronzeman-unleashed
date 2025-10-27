package com.elertan.panel2.screens.setup;

import com.elertan.ui.Bindings;
import com.google.inject.Inject;
import com.google.inject.Provider;

import javax.swing.*;
import java.awt.*;

public class RemoteStepScreen extends JPanel implements AutoCloseable {
    private final RemoteStepScreenViewModel viewModel;
    private final AutoCloseable cardLayoutBinding;

    @Inject
    public RemoteStepScreen(Provider<RemoteStepScreenViewModel> viewModelProvider) {
        viewModel = viewModelProvider.get();

        CardLayout cardLayout = new CardLayout();
        setLayout(cardLayout);

        cardLayoutBinding = Bindings.bindCardLayout(this, cardLayout, viewModel.stateView, this::buildStateView);
    }

    @Override
    public void close() throws Exception {
        cardLayoutBinding.close();
        viewModel.close();
    }

    private JPanel buildStateView(RemoteStepScreenViewModel.StateView stateView) {
        switch (stateView) {
            case ENTRY:
                break;
            case CHECKING:
                break;
        }

        throw new IllegalArgumentException( "Unknown state view: " + stateView);
    }
}
