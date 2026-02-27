package com.elertan.panel.screens.main;

import com.elertan.AccountConfigurationService;
import com.elertan.GameRulesService;
import com.elertan.ItemUnlockService;
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
    private final ItemUnlockService itemUnlockService;
    private final Runnable navigateToMainScreen;
    private GameRules gameRules;
    private Supplier<GameRulesEditorViewModel.Props> propsSupplier;

    private ConfigScreenViewModel(Client client, AccountConfigurationService accountConfigurationService,
        GameRulesService gameRulesService, GameRulesDataProvider gameRulesDataProvider,
        MemberService memberService, ItemUnlockService itemUnlockService, Runnable navigateToMainScreen) {
        this.accountConfigurationService = accountConfigurationService;
        this.memberService = memberService;
        this.gameRulesDataProvider = gameRulesDataProvider;
        this.itemUnlockService = itemUnlockService;
        this.navigateToMainScreen = navigateToMainScreen;

        propsSupplier = () -> {
            GameRules rules = gameRulesService.getGameRules().get();
            Member member = null;
            try { member = memberService.getMyMember(); } catch (Exception ignored) {}
            boolean viewOnly = member == null || member.getRole() != MemberRole.Owner;
            return new GameRulesEditorViewModel.Props(client.getAccountHash(), rules, this::setGameRules, viewOnly);
        };

        gameRulesEditorViewModelPropsProperty = new Property<>(propsSupplier.get());
        gameRulesService.waitUntilGameRulesReady(null).whenComplete((__, throwable) -> {
            if (throwable != null) { log.error("error waiting for game rules to be ready", throwable); return; }
            setGameRules(gameRulesService.getGameRules().get());
            gameRulesEditorViewModelPropsProperty.set(propsSupplier.get());
        });
    }

    public void onBackButtonClick() {
        gameRulesEditorViewModelPropsProperty.set(propsSupplier.get());
        navigateToMainScreen.run();
    }

    public void updateGameRulesClick() {
        int result = JOptionPane.showConfirmDialog(null,
            "Are you sure you want to update the game rules?",
            "Confirm update game rules", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        isSubmittingProperty.set(true);
        gameRulesDataProvider.updateGameRules(gameRules).whenComplete((__, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("An error occurred while trying to save the game rules.", throwable);
                    errorMessageProperty.set("An error occurred while trying to save the game rules.");
                    return;
                }
                errorMessageProperty.set(null);
                navigateToMainScreen.run();
            } finally {
                isSubmittingProperty.set(false);
            }
        });
    }

    public void resetUnlockedItemsClick() {
        int result = JOptionPane.showConfirmDialog(null,
            "This will wipe ALL unlocked items stored for this account.\n\n"
                + "Only items currently in your bank and inventory will be unlocked again automatically.\n\n"
                + "Are you sure you want to reset unlocked items?",
            "Reset unlocked items", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        isSubmittingProperty.set(true);
        itemUnlockService.resetUnlockedItemsFromCurrentState().whenComplete((__, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("An error occurred while resetting unlocked items.", throwable);
                    errorMessageProperty.set("An error occurred while resetting unlocked items.");
                    return;
                }
                errorMessageProperty.set(null);
            } finally {
                isSubmittingProperty.set(false);
            }
        });
    }

    public void stopQueuedUnlockPopupsClick() {
        int result = JOptionPane.showConfirmDialog(null,
            "Stop showing all currently queued \"Item Unlocked\" pop-ups?\n\n"
                + "This will clear the current queue of unlock messages. Future unlocks will still be shown.",
            "Stop unlock pop-ups", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        itemUnlockService.clearQueuedUnlockPopups();
    }

    private void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
        log.debug("config screen set game rules: {}", gameRules);
    }

    public void leaveButtonClick() {
        boolean isPlayingAlone = memberService.isPlayingAlone();
        Member member = memberService.getMyMember();

        StringBuilder msg = new StringBuilder();
        msg.append("Are you sure you want to leave?\n");
        msg.append("You will no longer be able to access the unlocked items panel and Bronzeman mode will be deactivated for your account.\n\n");
        if (!isPlayingAlone) {
            msg.append("You will also be removed from the group");
            if (member.getRole() == MemberRole.Owner)
                msg.append(", and will pass on the ownership of the group to the member who has been in the group the longest");
            msg.append(".\n\n");
        }
        msg.append("This will NOT delete the data associated with your progress, you can simply re-open the panel and get going through the setup again.");

        int result = JOptionPane.showConfirmDialog(null, msg.toString(),
            "Confirm Leave Bronzeman", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        isSubmittingProperty.set(true);
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!isPlayingAlone) {
            memberService.leaveGroupAndPromoteOldestMember().whenComplete((__, throwable) -> {
                if (throwable != null) future.completeExceptionally(throwable);
                else future.complete(null);
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
        @Inject private Client client;
        @Inject private AccountConfigurationService accountConfigurationService;
        @Inject private GameRulesService gameRulesService;
        @Inject private GameRulesDataProvider gameRulesDataProvider;
        @Inject private MemberService memberService;
        @Inject private ItemUnlockService itemUnlockService;

        @Override
        public ConfigScreenViewModel create(Runnable navigateToMainScreen) {
            return new ConfigScreenViewModel(client, accountConfigurationService,
                gameRulesService, gameRulesDataProvider, memberService, itemUnlockService, navigateToMainScreen);
        }
    }
}
