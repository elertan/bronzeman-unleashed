package com.elertan.panel.screens.setup;

import com.elertan.models.GameRules;
import com.elertan.panel.components.ErrorLabel;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Slf4j
public class GameRulesStepView extends JPanel implements AutoCloseable {

    private final Listener listener;
    private final Property<Boolean> isSubmitting = new Property<>(false);
    private final Property<String> errorMessage = new Property<>(null);
    private final ErrorLabel errorLabel;
    private final AutoCloseable backButtonEnabledBinding;
    private final AutoCloseable finishButtonEnabledBinding;

    private GameRulesStepView(Listener listener, GameRulesEditor gameRulesEditor) {
        this.listener = listener;
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
            Integer.MAX_VALUE, gameRulesEditor.getPreferredSize().height));
        add(gameRulesEditor);

        errorLabel = new ErrorLabel(errorMessage);
        errorLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        add(errorLabel);

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);

        JButton backButton = new JButton("Go back");
        backButton.addActionListener(e -> listener.onBack());
        backButtonEnabledBinding = Bindings.bindEnabled(backButton, isSubmitting.derive(b -> !b));
        buttonRow.add(backButton);
        buttonRow.add(Box.createHorizontalGlue());

        JButton finishButton = new JButton("Finish");
        finishButton.addActionListener(e -> onFinishButtonClicked());
        finishButtonEnabledBinding = Bindings.bindEnabled(finishButton, isSubmitting.derive(b -> !b));
        buttonRow.add(finishButton);
        add(buttonRow);

        add(Box.createVerticalGlue());
    }

    private void onFinishButtonClicked() {
        isSubmitting.set(true);
        listener.onFinish().whenComplete((__, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("error saving game rules", throwable);
                    errorMessage.set("An error occurred while trying to save the game rules.");
                    return;
                }
                errorMessage.set(null);
            } finally {
                isSubmitting.set(false);
            }
        });
    }

    @Override
    public void close() throws Exception {
        errorLabel.close();
        finishButtonEnabledBinding.close();
        backButtonEnabledBinding.close();
    }

    public interface Listener {
        void onBack();
        CompletableFuture<Void> onFinish();
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        GameRulesStepView create(Property<GameRules> gameRules, Listener listener,
            Property<Boolean> gameRulesAreViewOnly);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private GameRulesEditor.Factory gameRulesEditorFactory;
        @Inject private GameRulesEditorViewModel.Factory gameRulesEditorViewModelFactory;
        @Inject private Client client;

        @Override
        public GameRulesStepView create(Property<GameRules> gameRules, Listener listener,
            Property<Boolean> gameRulesAreViewOnly) {
            Supplier<GameRulesEditorViewModel.Props> makeProps = () -> {
                Boolean viewOnly = gameRulesAreViewOnly.get();
                return new GameRulesEditorViewModel.Props(
                    client.getAccountHash(), gameRules.get(), gameRules::set,
                    viewOnly != null && viewOnly);
            };
            GameRulesEditorViewModel vm = gameRulesEditorViewModelFactory.create(makeProps.get());
            gameRules.addListener(e -> vm.setProps(makeProps.get()));
            gameRulesAreViewOnly.addListener(e -> vm.setProps(makeProps.get()));
            return new GameRulesStepView(listener, gameRulesEditorFactory.create(vm));
        }
    }
}
