package com.elertan.panel2.screens.setup;

import com.elertan.panel2.screens.setup.remoteStep.EntryView;
import com.elertan.ui.Bindings;
import com.google.inject.Inject;
import com.google.inject.Provider;

import javax.swing.*;
import java.awt.*;

public class RemoteStepView extends JPanel implements AutoCloseable {
    private final RemoteStepViewViewModel viewModel;
    private final Provider<EntryView> entryViewProvider;

    private final AutoCloseable stateViewCardLayoutBinding;

    @Inject
    public RemoteStepView(Provider<RemoteStepViewViewModel> viewModelProvider, Provider<EntryView> entryViewProvider) {
        viewModel = viewModelProvider.get();
        this.entryViewProvider = entryViewProvider;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        // Title/header wrapper (stays static above cards)
        JLabel titleLabel = new JLabel("Remote Configuration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(10));
        add(header, BorderLayout.NORTH);

        CardLayout stateViewCardLayout = new CardLayout();
        JPanel stateVIewPanel = new JPanel(stateViewCardLayout);
        stateViewCardLayoutBinding = Bindings.bindCardLayout(stateVIewPanel, stateViewCardLayout, viewModel.stateView, this::buildStateView);

        add(stateVIewPanel, BorderLayout.CENTER);
    }

    @Override
    public void close() throws Exception {
        stateViewCardLayoutBinding.close();
        viewModel.close();
    }

    private JPanel buildStateView(RemoteStepViewViewModel.StateView stateView) {
        switch (stateView) {
            case ENTRY:
                return entryViewProvider.get();
            case CHECKING:
                break;
        }

        throw new IllegalArgumentException( "Unknown state view: " + stateView);
    }
}
