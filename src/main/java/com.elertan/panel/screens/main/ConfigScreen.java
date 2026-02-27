package com.elertan.panel.screens.main;

import com.elertan.panel.ViewportWidthTrackingPanel;
import com.elertan.panel.components.ErrorLabel;
import com.elertan.panel.components.GameRulesEditor;
import com.elertan.panel.components.GameRulesEditorViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public class ConfigScreen extends JPanel implements AutoCloseable {

    private final AutoCloseable backButtonEnabledBinding;
    private final AutoCloseable stopPopupsButtonEnabledBinding;
    private final AutoCloseable resetUnlockedButtonEnabledBinding;
    private final AutoCloseable updateGameRulesButtonEnabledBinding;
    private final AutoCloseable leaveButtonEnabledBinding;
    private final ErrorLabel errorLabel;

    private ConfigScreen(ConfigScreenViewModel viewModel,
        GameRulesEditorViewModel gameRulesEditorViewModel,
        GameRulesEditor.Factory gameRulesEditorFactory) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JButton backButton = new JButton("Back");
        backButton.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        backButton.addActionListener(e -> viewModel.onBackButtonClick());
        backButtonEnabledBinding = Bindings.bindEnabled(backButton, viewModel.isSubmittingProperty.derive(b -> !b));
        add(backButton, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(10), gbc);
        gbc.gridy++;

        JLabel titleLabel = new JLabel("Game Rules");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(titleLabel, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(10), gbc);
        gbc.gridy++;

        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        GameRulesEditor gameRulesEditor = gameRulesEditorFactory.create(gameRulesEditorViewModel);
        ViewportWidthTrackingPanel viewportWrapper = new ViewportWidthTrackingPanel(new java.awt.BorderLayout());
        viewportWrapper.add(gameRulesEditor, java.awt.BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(viewportWrapper);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            boolean visible = scrollPane.getVerticalScrollBar().isVisible();
            viewportWrapper.setBorder(visible
                ? BorderFactory.createEmptyBorder(0, 0, 0, 10)
                : BorderFactory.createEmptyBorder(0, 0, 0, 0));
            viewportWrapper.revalidate();
        });
        add(scrollPane, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        gbc.gridy++;

        add(Box.createVerticalStrut(5), gbc);
        gbc.gridy++;

        errorLabel = new ErrorLabel(viewModel.errorMessageProperty);
        add(errorLabel, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(5), gbc);
        gbc.gridy++;

        JButton stopPopupsButton = new JButton("Stop Item Unlock Popups");
        stopPopupsButton.addActionListener(e -> viewModel.stopQueuedUnlockPopupsClick());
        stopPopupsButtonEnabledBinding = Bindings.bindEnabled(stopPopupsButton,
            viewModel.isSubmittingProperty.derive(b -> !b));
        add(stopPopupsButton, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(5), gbc);
        gbc.gridy++;

        JButton resetUnlockedButton = new JButton("Reset Unlocked Items");
        resetUnlockedButton.addActionListener(e -> viewModel.resetUnlockedItemsClick());
        resetUnlockedButtonEnabledBinding = Bindings.bindEnabled(resetUnlockedButton,
            viewModel.isSubmittingProperty.derive(b -> !b));
        add(resetUnlockedButton, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(5), gbc);
        gbc.gridy++;

        JButton updateGameRulesButton = new JButton("Update Game Rules");
        updateGameRulesButton.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        updateGameRulesButton.addActionListener(e -> viewModel.updateGameRulesClick());
        updateGameRulesButtonEnabledBinding = Bindings.bindEnabled(updateGameRulesButton,
            Property.deriveMany(
                Arrays.asList(viewModel.gameRulesEditorViewModelPropsProperty, viewModel.isSubmittingProperty),
                values -> {
                    GameRulesEditorViewModel.Props props = viewModel.gameRulesEditorViewModelPropsProperty.get();
                    Boolean isSubmitting = viewModel.isSubmittingProperty.get();
                    if (props == null || isSubmitting == null) return false;
                    return !props.isViewOnlyMode() && !isSubmitting;
                }));
        add(updateGameRulesButton, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(15), gbc);
        gbc.gridy++;

        JButton leaveButton = new JButton("Leave Bronzeman");
        leaveButton.setBackground(new Color(183, 48, 48));
        leaveButton.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        leaveButton.addActionListener(e -> viewModel.leaveButtonClick());
        leaveButtonEnabledBinding = Bindings.bindEnabled(leaveButton, viewModel.isSubmittingProperty.derive(b -> !b));
        add(leaveButton, gbc);
    }

    @Override
    public void close() throws Exception {
        leaveButtonEnabledBinding.close();
        updateGameRulesButtonEnabledBinding.close();
        resetUnlockedButtonEnabledBinding.close();
        stopPopupsButtonEnabledBinding.close();
        errorLabel.close();
        backButtonEnabledBinding.close();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        ConfigScreen create(ConfigScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private GameRulesEditor.Factory gameRulesEditorFactory;
        @Inject private GameRulesEditorViewModel.Factory gameRulesEditorViewModelFactory;

        @Override
        public ConfigScreen create(ConfigScreenViewModel viewModel) {
            GameRulesEditorViewModel vm = gameRulesEditorViewModelFactory.create(
                viewModel.gameRulesEditorViewModelPropsProperty.get());
            viewModel.gameRulesEditorViewModelPropsProperty.addListener(
                event -> vm.setProps(viewModel.gameRulesEditorViewModelPropsProperty.get()));
            vm.setProps(viewModel.gameRulesEditorViewModelPropsProperty.get());
            return new ConfigScreen(viewModel, vm, gameRulesEditorFactory);
        }
    }
}
