package com.elertan.panel.screens.setup;

import com.elertan.panel.components.GameRulesEditor;
import com.elertan.panel.components.GameRulesEditorViewModel;
import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class GameRulesStepView extends JPanel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        GameRulesStepView create(GameRulesStepViewViewModel viewModel, Property<Boolean> gameRulesAreViewOnly);
    }

    @Slf4j
    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject
        private Client client;
        @Inject
        GameRulesEditor.Factory gameRulesEditorFactory;
        @Inject
        GameRulesEditorViewModel.Factory gameRulesEditorViewModelFactory;

        @Override
        public GameRulesStepView create(GameRulesStepViewViewModel viewModel, Property<Boolean> gameRulesAreViewOnly) {
            Supplier<GameRulesEditorViewModel.Props> makeProps = () -> {
                Boolean gameRulesAreViewOnlyValue = gameRulesAreViewOnly.get();
                return new GameRulesEditorViewModel.Props(
                        client.getAccountHash(),
                        viewModel.gameRules.get(),
                        viewModel.gameRules::set,
                        gameRulesAreViewOnlyValue != null && gameRulesAreViewOnlyValue
                );
            };
            GameRulesEditorViewModel gameRulesEditorViewModel = gameRulesEditorViewModelFactory.create(makeProps.get());

            viewModel.gameRules.addListener((event) -> gameRulesEditorViewModel.setProps(makeProps.get()));
            gameRulesAreViewOnly.addListener((event) -> gameRulesEditorViewModel.setProps(makeProps.get()));

            GameRulesEditor gameRulesEditor = gameRulesEditorFactory.create(gameRulesEditorViewModel);
            return new GameRulesStepView(viewModel, gameRulesEditor);
        }
    }

    private final AutoCloseable backButtonEnabledBinding;
    private final AutoCloseable finishButtonEnabledBinding;

    private GameRulesStepView(GameRulesStepViewViewModel viewModel, GameRulesEditor gameRulesEditor) {
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

        gameRulesEditor.setMaximumSize(new Dimension(Integer.MAX_VALUE, gameRulesEditor.getPreferredSize().height));
        add(gameRulesEditor);

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);

        JButton backButton = new JButton("Go back");
        backButton.addActionListener(e -> viewModel.onBackButtonClicked());
        backButtonEnabledBinding = Bindings.bindEnabled(backButton, viewModel.isSubmitting.derive(b -> !b));
        buttonRow.add(backButton);

        buttonRow.add(Box.createHorizontalGlue());

        JButton finishButton = new JButton("Finish");
        finishButton.addActionListener(e -> viewModel.onFinishButtonClicked());
        finishButtonEnabledBinding = Bindings.bindEnabled(finishButton, viewModel.isSubmitting.derive(b -> !b));
        buttonRow.add(finishButton);

        add(buttonRow);

        add(Box.createVerticalGlue());
    }

    @Override
    public void close() throws Exception {
        finishButtonEnabledBinding.close();
        backButtonEnabledBinding.close();
    }
}
