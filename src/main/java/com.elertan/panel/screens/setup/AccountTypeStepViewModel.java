package com.elertan.panel.screens.setup;

import com.elertan.models.StartMode;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountTypeStepViewModel {

    /** This player already chose "Existing account" before, for example before a reinstall. */
    public final Property<Boolean> isExistingAccountSaved;
    /** The group rule `requireItemLock` applies to this player. */
    public final Property<Boolean> isItemLockRequired;
    public final Property<StartMode> selectedStartMode = new Property<>(StartMode.NEW_ACCOUNT);
    public final Property<Boolean> isDisclaimerAccepted = new Property<>(false);
    public final Property<Boolean> isSubmitting = new Property<>(false);
    public final Property<String> errorMessage = new Property<>(null);
    public final Property<Boolean> canFinish;
    private final Listener listener;

    private AccountTypeStepViewModel(
        Property<Boolean> isExistingAccountSaved,
        Property<Boolean> isItemLockRequired,
        Listener listener
    ) {
        this.isExistingAccountSaved = isExistingAccountSaved;
        this.isItemLockRequired = isItemLockRequired;
        this.listener = listener;

        // A saved choice was confirmed before, so it needs no disclaimer again.
        canFinish = Property.deriveMany(
            Arrays.asList(selectedStartMode, isDisclaimerAccepted, isSubmitting, isExistingAccountSaved),
            values -> !Boolean.TRUE.equals(values.get(2)) && (Boolean.TRUE.equals(values.get(3))
                || values.get(0) == StartMode.NEW_ACCOUNT || Boolean.TRUE.equals(values.get(1)))
        );
        selectLockedChoice();
        isExistingAccountSaved.addListener(e -> selectLockedChoice());
        isItemLockRequired.addListener(e -> selectLockedChoice());
    }

    /** A saved choice and the group rule both force "Existing account". */
    public boolean isLockedToExistingAccount() {
        return Boolean.TRUE.equals(isExistingAccountSaved.get()) || Boolean.TRUE.equals(isItemLockRequired.get());
    }

    private void selectLockedChoice() {
        if (isLockedToExistingAccount()) {
            selectedStartMode.set(StartMode.EXISTING_ACCOUNT);
        }
    }

    public void onStartModeSelected(StartMode startMode) {
        if (!isLockedToExistingAccount()) {
            selectedStartMode.set(startMode);
        }
    }

    public void onBackButtonClicked() {
        listener.onBack();
    }

    public void onFinishButtonClicked() {
        if (!Boolean.TRUE.equals(canFinish.get())) {
            return;
        }
        isSubmitting.set(true);
        listener.onFinish(selectedStartMode.get()).whenComplete((__, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("error finishing setup", throwable);
                    errorMessage.set("An error occurred while trying to finish setup.");
                    return;
                }
                errorMessage.set(null);
            } finally {
                isSubmitting.set(false);
            }
        });
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        AccountTypeStepViewModel create(
            Property<Boolean> isExistingAccountSaved,
            Property<Boolean> isItemLockRequired,
            Listener listener
        );
    }

    public interface Listener {

        void onBack();

        CompletableFuture<Void> onFinish(StartMode startMode);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Override
        public AccountTypeStepViewModel create(
            Property<Boolean> isExistingAccountSaved,
            Property<Boolean> isItemLockRequired,
            Listener listener
        ) {
            return new AccountTypeStepViewModel(isExistingAccountSaved, isItemLockRequired, listener);
        }
    }
}
