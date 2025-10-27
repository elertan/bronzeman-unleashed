package com.elertan.panel2.screens;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPanelService;
import com.elertan.panel2.screens.setup.RemoteStepScreen;
import com.elertan.ui.Bindings;
import com.google.inject.Inject;
import com.google.inject.Provider;

import javax.swing.*;
import java.awt.*;

public class SetupScreen extends JPanel implements AutoCloseable {
    private final SetupScreenViewModel viewModel;
    private final Provider<RemoteStepScreen> remoteStepScreenProvider;
    private final AutoCloseable contentCardLayoutBinding;

    @Inject
    public SetupScreen(
            Provider<SetupScreenViewModel> viewModelProvider,
            Provider<RemoteStepScreen> remoteStepScreenProvider,
            BUPanelService buPanelService,
            AccountConfigurationService accountConfigurationService
    ) {
        viewModel = viewModelProvider.get();
        this.remoteStepScreenProvider = remoteStepScreenProvider;

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
        getStartedLabel.setText("<html><div style=\"text-align:center;\">Let's get you started by configuring settings for your account.</div></html>");
        getStartedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(getStartedLabel);

        inner.add(Box.createVerticalStrut(10));

        CardLayout contentCardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(contentCardLayout);

        contentCardLayoutBinding = Bindings.bindCardLayout(contentPanel, contentCardLayout, viewModel.step, this::buildStep);

        inner.add(Box.createVerticalGlue());

        JButton dontAskMeAgainButton = new JButton("Don't ask me again for this account");
        dontAskMeAgainButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        dontAskMeAgainButton.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        dontAskMeAgainButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, dontAskMeAgainButton.getPreferredSize().height));
        dontAskMeAgainButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    null,
                    "We won't ask you again to set up bronzeman mode for this account.\n"
                            + "You can set up bronzeman mode at any time by re-opening this panel.",
                    "Confirm setup choice",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.OK_OPTION) {
                buPanelService.closePanel();
                accountConfigurationService.addCurrentAccountHashToAutoOpenConfigurationDisabled();
            }
        });
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
                return remoteStepScreenProvider.get();
            case GAME_RULES:
                return new JPanel();
        }

        throw new IllegalStateException("Unknown step: " + step);
    }
}
