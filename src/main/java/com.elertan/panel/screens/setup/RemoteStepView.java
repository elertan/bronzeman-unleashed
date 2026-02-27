package com.elertan.panel.screens.setup;

import com.elertan.panel.screens.setup.remoteStep.CheckingView;
import com.elertan.panel.screens.setup.remoteStep.EntryView;
import com.elertan.panel.screens.setup.remoteStep.EntryViewViewModel;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class RemoteStepView extends JPanel implements AutoCloseable {

    private final RemoteStepViewViewModel viewModel;
    private final EntryViewViewModel entryViewViewModel;
    private final AutoCloseable stateViewCardLayoutBinding;

    private RemoteStepView(RemoteStepViewViewModel viewModel, EntryViewViewModel entryViewViewModel) {
        this.viewModel = viewModel;
        this.entryViewViewModel = entryViewViewModel;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

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
        JPanel stateViewPanel = new JPanel(stateViewCardLayout);
        stateViewCardLayoutBinding = Bindings.bindCardLayout(
            stateViewPanel, stateViewCardLayout, viewModel.stateView, this::buildStateView);
        add(stateViewPanel, BorderLayout.CENTER);
    }

    @Override
    public void close() throws Exception {
        stateViewCardLayoutBinding.close();
        viewModel.close();
    }

    private JPanel buildStateView(RemoteStepViewViewModel.StateView stateView) {
        switch (stateView) {
            case ENTRY: return new EntryView(entryViewViewModel);
            case CHECKING: return new CheckingView(viewModel::onCancelChecking);
        }
        throw new IllegalArgumentException("Unknown state view: " + stateView);
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        RemoteStepView create(RemoteStepViewViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private EntryViewViewModel.Factory entryViewViewModelFactory;

        @Override
        public RemoteStepView create(RemoteStepViewViewModel viewModel) {
            return new RemoteStepView(viewModel,
                entryViewViewModelFactory.create(viewModel::onEntryViewTrySubmit));
        }
    }
}
