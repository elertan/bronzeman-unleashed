package com.elertan.panel.screens.setup;

import com.elertan.panel.components.GameRulesEditor;
import com.elertan.panel.components.GameRulesEditorViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

public class GameRulesStepView extends JPanel implements AutoCloseable {

    private final AutoCloseable backButtonEnabledBinding;
    private final AutoCloseable finishButtonEnabledBinding;
    private final AutoCloseable errorMessageContainerVisibleBinding;
    private final AutoCloseable errorMessageLabelTextBinding;

    private GameRulesStepView(GameRulesStepViewViewModel viewModel,
        GameRulesEditor gameRulesEditor) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JLabel titleLabel = new JLabel("Game Rules");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(10));
        add(header);

        gameRulesEditor.setMaximumSize(new Dimension(
            Integer.MAX_VALUE,
            gameRulesEditor.getPreferredSize().height
        ));
        add(gameRulesEditor);

        JPanel errorMessageContainer = new JPanel();
        errorMessageContainer.setLayout(new BoxLayout(errorMessageContainer, BoxLayout.Y_AXIS));
        errorMessageContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        errorMessageContainerVisibleBinding = Bindings.bindVisible(
            errorMessageContainer,
            viewModel.errorMessage.derive(errorMessage -> errorMessage != null
                && !errorMessage.isEmpty())
        );

        JLabel errorMessageLabel = new JLabel();
        errorMessageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorMessageLabelTextBinding = Bindings.bindLabelText(
            errorMessageLabel, viewModel.errorMessage.derive(errorMessage -> {
                if (errorMessage == null || errorMessage.isEmpty()) {
                    return "";
                }

                String sb = "<html><div style=\"text-align:center;color:red;\">" +
                    errorMessage +
                    "</div></html>";

                return sb;
            })
        );
        errorMessageContainer.add(errorMessageLabel);
        add(errorMessageContainer);

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);

        JButton backButton = new JButton("Go back");
        backButton.addActionListener(e -> viewModel.onBackButtonClicked());
        backButtonEnabledBinding = Bindings.bindEnabled(
            backButton,
            viewModel.isSubmitting.derive(b -> !b)
        );
        buttonRow.add(backButton);

        buttonRow.add(Box.createHorizontalGlue());

        JButton finishButton = new JButton("Finish");
        finishButton.addActionListener(e -> viewModel.onFinishButtonClicked());
        finishButtonEnabledBinding = Bindings.bindEnabled(
            finishButton,
            viewModel.isSubmitting.derive(b -> !b)
        );
        buttonRow.add(finishButton);

        add(buttonRow);

        add(Box.createVerticalGlue());
    }

    @Override
    public void close() throws Exception {
        errorMessageLabelTextBinding.close();
        errorMessageContainerVisibleBinding.close();
        finishButtonEnabledBinding.close();
        backButtonEnabledBinding.close();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        GameRulesStepView create(GameRulesStepViewViewModel viewModel,
            Property<Boolean> gameRulesAreViewOnly);
    }

    @Slf4j
    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        GameRulesEditor.Factory gameRulesEditorFactory;
        @Inject
        GameRulesEditorViewModel.Factory gameRulesEditorViewModelFactory;
        @Inject
        private Client client;

        @Override
        public GameRulesStepView create(GameRulesStepViewViewModel viewModel,
            Property<Boolean> gameRulesAreViewOnly) {
            Supplier<GameRulesEditorViewModel.Props> makeProps = () -> {
                Boolean gameRulesAreViewOnlyValue = gameRulesAreViewOnly.get();
                return new GameRulesEditorViewModel.Props(
                    client.getAccountHash(),
                    viewModel.gameRules.get(),
                    viewModel.gameRules::set,
                    gameRulesAreViewOnlyValue != null && gameRulesAreViewOnlyValue
                );
            };
            GameRulesEditorViewModel gameRulesEditorViewModel = gameRulesEditorViewModelFactory.create(
                makeProps.get());

            viewModel.gameRules.addListener((event) -> gameRulesEditorViewModel.setProps(makeProps.get()));
            gameRulesAreViewOnly.addListener((event) -> gameRulesEditorViewModel.setProps(makeProps.get()));

            GameRulesEditor gameRulesEditor = gameRulesEditorFactory.create(gameRulesEditorViewModel);
            return new GameRulesStepView(viewModel, gameRulesEditor);
        }
    }
}
