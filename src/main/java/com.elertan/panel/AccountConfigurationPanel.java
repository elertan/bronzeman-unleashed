package com.elertan.panel;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPanelService;
import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.panel.accountConfiguration.GameRulesConfigurationView;
import com.elertan.panel.accountConfiguration.RemoteConfigurationView;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AccountConfigurationPanel extends JPanel {
    private enum SetupStep {
        REMOTE_CONFIGURATION,
        GAME_RULES_CONFIGURATION,
    }

    private final CardLayout layout = new CardLayout();
    private final JPanel contentPanel = new JPanel(layout);

    private SetupStep currentStep = SetupStep.REMOTE_CONFIGURATION;

    private FirebaseRealtimeDatabaseURL firebaseRealtimeDatabaseURL;
    private GameRules gameRules;

    public AccountConfigurationPanel(OkHttpClient httpClient, ClientThread clientThread, Gson gson, BUPanelService buPanelService, AccountConfigurationService accountConfigurationService, GameRulesDataProvider gameRulesDataProvider) {
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

        GameRulesConfigurationView gameRulesConfigurationView = new GameRulesConfigurationView(new GameRulesConfigurationView.Listener() {
            @Override
            public void onBack() {
                setCurrentStep(SetupStep.REMOTE_CONFIGURATION);
            }

            @Override
            public CompletableFuture<Void> onSuccess(GameRules rules, boolean isViewOnlyMode) {
                gameRules = rules;
                CompletableFuture<Void> future = new CompletableFuture<>();

                AccountConfiguration config = new AccountConfiguration(firebaseRealtimeDatabaseURL);
                accountConfigurationService.setCurrentAccountConfiguration(config);

                log.info("current account config set");

                if (isViewOnlyMode) {
                    future.complete(null);
                    setCurrentStep(SetupStep.REMOTE_CONFIGURATION);
                } else {
                    gameRulesDataProvider.waitUntilReady(Duration.ofSeconds(10))
                            .thenCompose(v -> gameRulesDataProvider.updateGameRules(rules))
                            .whenComplete((ignored, throwable) -> {
                                if (throwable != null) {
                                    future.completeExceptionally(throwable);
                                    return;
                                }

                                log.info("Game rules updated successfully");

                                future.complete(null);
                                setCurrentStep(SetupStep.REMOTE_CONFIGURATION);
                            });
                }

                return future;
            }
        });

        RemoteConfigurationView remoteConfigurationView = new RemoteConfigurationView(httpClient, clientThread, gson, (url, gameRules) -> {
            firebaseRealtimeDatabaseURL = url;

            gameRulesConfigurationView.applyGameRules(gameRules);
            setCurrentStep(SetupStep.GAME_RULES_CONFIGURATION);
        });

        contentPanel.add(remoteConfigurationView, SetupStep.REMOTE_CONFIGURATION.name());
        contentPanel.add(gameRulesConfigurationView, SetupStep.GAME_RULES_CONFIGURATION.name());
        inner.add(contentPanel);

        // Show initial step
        layout.show(contentPanel, currentStep.name());

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

    private void setCurrentStep(SetupStep step) {
        this.currentStep = step;
        layout.show(contentPanel, step.name());
        contentPanel.revalidate();
        contentPanel.repaint();
    }

}
