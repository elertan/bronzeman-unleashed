package com.elertan.panel2.screens.setup;

import com.elertan.panel2.components.GameRulesEditor;
import com.elertan.panel2.components.GameRulesEditorViewModel;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class GameRulesStepView extends JPanel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        GameRulesStepView create(GameRulesStepViewViewModel viewModel);
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
        public GameRulesStepView create(GameRulesStepViewViewModel viewModel) {
            Supplier<GameRulesEditorViewModel.Props> makeProps = () -> new GameRulesEditorViewModel.Props(
                    client.getAccountHash(),
                    viewModel.gameRules.get(),
                    viewModel.gameRules::set,
                    false
            );
            GameRulesEditorViewModel gameRulesEditorViewModel = gameRulesEditorViewModelFactory.create(makeProps.get());

            viewModel.gameRules.addListener((event) -> gameRulesEditorViewModel.setProps(makeProps.get()));

            GameRulesEditor gameRulesEditor = gameRulesEditorFactory.create(gameRulesEditorViewModel);
            return new GameRulesStepView(viewModel, gameRulesEditor);
        }
    }

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
        buttonRow.add(backButton);

        buttonRow.add(Box.createHorizontalGlue());

        JButton finishButton = new JButton("Finish");
        finishButton.addActionListener(e -> viewModel.onFinishButtonClicked());
        buttonRow.add(finishButton);

        add(buttonRow);

        add(Box.createVerticalGlue());
    }
}
