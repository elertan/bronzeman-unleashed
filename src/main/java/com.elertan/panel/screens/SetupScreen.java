package com.elertan.panel.screens;

import com.elertan.panel.ViewportWidthTrackingPanel;
import com.elertan.panel.screens.setup.GameRulesStepView;
import com.elertan.panel.screens.setup.GameRulesStepViewViewModel;
import com.elertan.panel.screens.setup.RemoteStepView;
import com.elertan.panel.screens.setup.RemoteStepViewViewModel;
import com.elertan.ui.Bindings;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public class SetupScreen extends JPanel implements AutoCloseable {

    private final SetupScreenViewModel viewModel;
    private final RemoteStepView.Factory remoteStepViewFactory;
    private final RemoteStepViewViewModel remoteStepViewViewModel;
    private final GameRulesStepView.Factory gameRulesStepViewFactory;
    private final GameRulesStepViewViewModel gameRulesStepViewViewModel;
    private final AutoCloseable contentCardLayoutBinding;

    private SetupScreen(
        SetupScreenViewModel viewModel,
        RemoteStepView.Factory remoteStepViewFactory,
        RemoteStepViewViewModel remoteStepViewViewModel,
        GameRulesStepView.Factory gameRulesStepViewFactory,
        GameRulesStepViewViewModel gameRulesStepViewViewModel
    ) {
        this.viewModel = viewModel;
        this.remoteStepViewFactory = remoteStepViewFactory;
        this.remoteStepViewViewModel = remoteStepViewViewModel;
        this.gameRulesStepViewFactory = gameRulesStepViewFactory;
        this.gameRulesStepViewViewModel = gameRulesStepViewViewModel;

        setLayout(new BorderLayout());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JLabel titleLabel = new JLabel("Bronzeman Unleashed");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Setup");
        subtitleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(subtitleLabel);

        inner.add(Box.createVerticalStrut(15));

        JLabel getStartedLabel = new JLabel();
        getStartedLabel.setText(
            "<html><div style=\"text-align:center;\">Let's get you started by configuring settings for your account.</div></html>");
        getStartedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(getStartedLabel);

        inner.add(Box.createVerticalStrut(10));

        CardLayout contentCardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(contentCardLayout);
        contentCardLayoutBinding = Bindings.bindCardLayout(
            contentPanel,
            contentCardLayout,
            viewModel.step,
            this::buildStep
        );

        ViewportWidthTrackingPanel viewportWrapper = new ViewportWidthTrackingPanel(new BorderLayout());
        viewportWrapper.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(viewportWrapper);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        // Dynamically add right padding only when vertical scrollbar is visible
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            boolean visible = scrollPane.getVerticalScrollBar().isVisible();
            if (visible) {
                viewportWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
            } else {
                viewportWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            }
            viewportWrapper.revalidate();
        });

        inner.add(scrollPane);

        inner.add(Box.createVerticalStrut(15));

        JButton dontAskMeAgainButton = new JButton("Don't ask me again for this account");
        dontAskMeAgainButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        dontAskMeAgainButton.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        dontAskMeAgainButton.setMaximumSize(new Dimension(
            Integer.MAX_VALUE,
            dontAskMeAgainButton.getPreferredSize().height
        ));
        dontAskMeAgainButton.addActionListener(e -> viewModel.onDontAskMeAgainButtonClick());
        inner.add(dontAskMeAgainButton);

        add(inner, BorderLayout.CENTER);
    }

    @Override
    public void close() throws Exception {
        contentCardLayoutBinding.close();
        viewModel.close();
    }

    private JPanel buildStep(SetupScreenViewModel.Step step) {
        switch (step) {
            case REMOTE:
                return remoteStepViewFactory.create(remoteStepViewViewModel);
            case GAME_RULES:
                return gameRulesStepViewFactory.create(
                    gameRulesStepViewViewModel,
                    viewModel.gameRulesAreViewOnly
                );
        }

        throw new IllegalStateException("Unknown step: " + step);
    }


    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        SetupScreen create(SetupScreenViewModel viewModel);
    }

    @Singleton
    static final class FactoryImpl implements Factory {

        @Inject
        RemoteStepView.Factory remoteStepViewFactory;
        @Inject
        RemoteStepViewViewModel.Factory remoteStepViewViewModelFactory;
        @Inject
        GameRulesStepView.Factory gameRulesStepViewFactory;
        @Inject
        GameRulesStepViewViewModel.Factory gameRulesStepViewViewModelFactory;

        @Override
        public SetupScreen create(SetupScreenViewModel viewModel) {
            RemoteStepViewViewModel remoteStepViewViewModel = remoteStepViewViewModelFactory.create(
                viewModel::onRemoteStepFinished);
            GameRulesStepViewViewModel gameRulesStepViewViewModel = gameRulesStepViewViewModelFactory.create(
                viewModel.gameRules, new GameRulesStepViewViewModel.Listener() {
                    @Override
                    public void onBack() {
                        viewModel.onGameRulesStepBack();
                    }

                    @Override
                    public CompletableFuture<Void> onFinish() {
                        return viewModel.onGameRulesStepFinish();
                    }
                }
            );
            return new SetupScreen(
                viewModel,
                remoteStepViewFactory,
                remoteStepViewViewModel,
                gameRulesStepViewFactory,
                gameRulesStepViewViewModel
            );
        }
    }
}
