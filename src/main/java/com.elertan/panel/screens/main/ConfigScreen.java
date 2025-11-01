package com.elertan.panel.screens.main;

import com.elertan.panel.components.GameRulesEditor;
import com.elertan.panel.components.GameRulesEditorViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.BorderLayout;
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
    private final AutoCloseable updateGameRulesButtonEnabledBinding;
    private final AutoCloseable errorMessageLabelVisibleBinding;
    private final AutoCloseable errorMessageLabelTextBinding;

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

        gbc.weighty = 0.0;
        JButton backButton = new JButton("Back");
        backButton.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        backButton.addActionListener(e -> viewModel.onBackButtonClick());
        backButtonEnabledBinding = Bindings.bindEnabled(
            backButton,
            viewModel.isSubmittingProperty.derive(b -> !b)
        );
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

        JPanel viewportWrapper = new JPanel(new BorderLayout());
        viewportWrapper.add(gameRulesEditor, BorderLayout.NORTH);

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

        add(scrollPane, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        gbc.gridy++;

        add(Box.createVerticalStrut(10), gbc);
        gbc.gridy++;

        JLabel errorMessageLabel = new JLabel();
        errorMessageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorMessageLabelVisibleBinding = Bindings.bindVisible(
            errorMessageLabel,
            viewModel.errorMessageProperty.derive(errorMessage -> errorMessage != null
                && !errorMessage.isEmpty())
        );
        errorMessageLabelTextBinding = Bindings.bindLabelText(
            errorMessageLabel, viewModel.errorMessageProperty.derive(errorMessage -> {
                if (errorMessage == null || errorMessage.isEmpty()) {
                    return "";
                }

                String sb = "<html><div style=\"text-align:center;color:red;\">" +
                    errorMessage +
                    "</div></html>";

                return sb;
            })
        );
        add(errorMessageLabel, gbc);
        gbc.gridy++;

        add(Box.createVerticalStrut(10), gbc);
        gbc.gridy++;

        gbc.weightx = 0.0;
        JButton updateGameRulesButton = new JButton("Update Game Rules");
        updateGameRulesButton.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        updateGameRulesButton.addActionListener(e -> viewModel.updateGameRulesClick());
        updateGameRulesButtonEnabledBinding = Bindings.bindEnabled(
            updateGameRulesButton,
            Property.deriveMany(
                Arrays.asList(
                    viewModel.gameRulesEditorViewModelPropsProperty,
                    viewModel.isSubmittingProperty
                ),
                (values) -> {
                    GameRulesEditorViewModel.Props props = viewModel.gameRulesEditorViewModelPropsProperty.get();
                    Boolean isSubmitting = viewModel.isSubmittingProperty.get();

                    if (props == null || isSubmitting == null) {
                        return false;
                    }

                    return !props.isViewOnlyMode() && !isSubmitting;
                }
            )
        );
        add(updateGameRulesButton, gbc);
        gbc.gridy++;

    }

    @Override
    public void close() throws Exception {
        errorMessageLabelVisibleBinding.close();
        errorMessageLabelTextBinding.close();
        updateGameRulesButtonEnabledBinding.close();
        backButtonEnabledBinding.close();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        ConfigScreen create(ConfigScreenViewModel viewModel);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private GameRulesEditor.Factory gameRulesEditorFactory;
        @Inject
        private GameRulesEditorViewModel.Factory gameRulesEditorViewModelFactory;

        @Override
        public ConfigScreen create(ConfigScreenViewModel viewModel) {
            GameRulesEditorViewModel.Props props = viewModel.gameRulesEditorViewModelPropsProperty.get();
            GameRulesEditorViewModel gameRulesEditorViewModel = gameRulesEditorViewModelFactory.create(
                props);

            viewModel.gameRulesEditorViewModelPropsProperty.addListener((event) -> {
                GameRulesEditorViewModel.Props newProps = viewModel.gameRulesEditorViewModelPropsProperty.get();
                gameRulesEditorViewModel.setProps(newProps);
            });

            return new ConfigScreen(viewModel, gameRulesEditorViewModel, gameRulesEditorFactory);
        }
    }
}
