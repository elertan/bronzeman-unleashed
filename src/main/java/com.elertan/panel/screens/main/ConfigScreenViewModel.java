package com.elertan.panel.screens.main;

import com.elertan.AccountConfigurationService;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Slf4j
public class ConfigScreenViewModel {

    public final Property<GameRulesEditorViewModel.Props> gameRulesEditorViewModelPropsProperty;
    public final Property<Boolean> isSubmittingProperty = new Property<>(false);
    public final Property<String> errorMessageProperty = new Property<>(null);

    private final AccountConfigurationService accountConfigurationService;
    private final MemberService memberService;
    private final GameRulesDataProvider gameRulesDataProvider;
    private final Runnable navigateToMainScreen;

    private GameRules gameRules;
    private Supplier<GameRulesEditorViewModel.Props> propsSupplier;

    private ConfigScreenViewModel(Client client,
        AccountConfigurationService accountConfigurationService, GameRulesService gameRulesService,
        GameRulesDataProvider gameRulesDataProvider, MemberService memberService,
        Runnable navigateToMainScreen) {
        this.accountConfigurationService = accountConfigurationService;
        this.memberService = memberService;
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
        log.debug("config screen set game rules: {}", gameRules);
    }

    public void leaveButtonClick() {
        boolean isPlayingAlone = memberService.isPlayingAlone();
        Member member = memberService.getMyMember();

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Are you sure you want to leave?\n");
        messageBuilder.append(
            "You will no longer be able to access the unlocked items panel and Bronzeman mode will be deactivated for your account.\n\n");
        if (!isPlayingAlone) {
            messageBuilder.append("You will also be removed from the group");
            if (member.getRole() == MemberRole.Owner) {
                messageBuilder.append(
                    ", and will pass on the ownership of the group to the member who has been in the group the longest");
            }
            messageBuilder.append(".\n\n");
        }
        messageBuilder.append(
            "This will NOT delete the data associated with your progress, you can simply re-open the panel and get going through the setup again.");

        // TODO: More stuff
        int result = JOptionPane.showConfirmDialog(
            null,
            messageBuilder.toString(),
            "Confirm Leave Bronzeman",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        isSubmittingProperty.set(true);

        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isPlayingAlone) {
            memberService.leaveGroupAndPromoteOldestMember().whenComplete((__, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                    return;
                }
                future.complete(null);
            });
        } else {
            future.complete(null);
        }

        future.whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("error leaving group", throwable);
                errorMessageProperty.set("An error occurred while trying to leave the group.");
                return;
            }
            errorMessageProperty.set(null);

            navigateToMainScreen.run();
            accountConfigurationService.setCurrentAccountConfiguration(null);
            isSubmittingProperty.set(false);
        });

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
        private AccountConfigurationService accountConfigurationService;
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
                accountConfigurationService,
                gameRulesService,
                gameRulesDataProvider,
                memberService,
                navigateToMainScreen
            );
        }
    }
}
