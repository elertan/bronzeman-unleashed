package com.elertan.panel.screens.main;

import com.elertan.GameRulesService;
import com.elertan.MemberService;
import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.GameRules;
import com.elertan.models.Member;
import com.elertan.models.MemberRole;
import com.elertan.panel.components.GameRulesEditorViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Slf4j
public class ConfigScreenViewModel {

    public final Property<GameRulesEditorViewModel.Props> gameRulesEditorViewModelPropsProperty;
    public final Property<Boolean> isSubmittingProperty = new Property<>(false);
    public final Property<String> errorMessageProperty = new Property<>(null);

    private final GameRulesDataProvider gameRulesDataProvider;
    private final Runnable navigateToMainScreen;

    private GameRules gameRules;
    private Supplier<GameRulesEditorViewModel.Props> propsSupplier;

    private ConfigScreenViewModel(Client client, GameRulesService gameRulesService,
        GameRulesDataProvider gameRulesDataProvider, MemberService memberService,
        Runnable navigateToMainScreen) {
        this.gameRulesDataProvider = gameRulesDataProvider;
        this.navigateToMainScreen = navigateToMainScreen;
        propsSupplier = () -> {
            GameRules gameRules = gameRulesService.getGameRules();
            Member member = null;
            try {
                member = memberService.getMyMember();
            } catch (Exception ignored) {
            }
            boolean isViewOnlyMode = member == null || member.getRole() != MemberRole.Owner;

            return new GameRulesEditorViewModel.Props(
                client.getAccountHash(),
                gameRules,
                (newGameRules) -> setGameRules(newGameRules),
                isViewOnlyMode
            );
        };

        gameRulesEditorViewModelPropsProperty = new Property<>(propsSupplier.get());
        gameRulesService.waitUntilGameRulesReady(null)
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("error waiting for game rules to be ready", throwable);
                    return;
                }
                setGameRules(gameRulesService.getGameRules());
                gameRulesEditorViewModelPropsProperty.set(propsSupplier.get());
            });
    }

    public void onBackButtonClick() {
        // Reset game rules to the last saved game rules.
        gameRulesEditorViewModelPropsProperty.set(propsSupplier.get());

        navigateToMainScreen.run();
    }

    public void updateGameRulesClick() {
        int result = JOptionPane.showConfirmDialog(
            null,
            "Are you sure you want to update the game rules?",
            "Confirm update game rules",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        isSubmittingProperty.set(true);

        gameRulesDataProvider.updateGameRules(gameRules)
            .whenComplete((__, throwable) -> {
                try {
                    if (throwable != null) {
                        log.error(
                            "An error occurred while trying to save the game rules.",
                            throwable
                        );
                        errorMessageProperty.set(
                            "An error occurred while trying to save the game rules.");
                        return;
                    }

                    errorMessageProperty.set(null);
                    navigateToMainScreen.run();
                } finally {
                    isSubmittingProperty.set(false);
                }
            });
    }

    private void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
        log.info("config screen set game rules: {}", gameRules);
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        ConfigScreenViewModel create(Runnable navigateToMainScreen);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private Client client;
        @Inject
        private GameRulesService gameRulesService;
        @Inject
        private GameRulesDataProvider gameRulesDataProvider;
        @Inject
        private MemberService memberService;

        @Override
        public ConfigScreenViewModel create(Runnable navigateToMainScreen) {
            return new ConfigScreenViewModel(
                client,
                gameRulesService,
                gameRulesDataProvider,
                memberService,
                navigateToMainScreen
            );
        }
    }
}
