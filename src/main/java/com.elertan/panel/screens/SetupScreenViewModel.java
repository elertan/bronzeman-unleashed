package com.elertan.panel.screens;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPanelService;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.models.AccountConfiguration.StorageMode;
import com.elertan.remote.local.LocalStorageSession;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.firebase.storageAdapters.GameRulesFirebaseObjectStorageAdapter;
import com.elertan.ui.Property;
import com.google.gson.Gson;
import java.io.IOException;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import okhttp3.OkHttpClient;

@Slf4j
public final class SetupScreenViewModel implements AutoCloseable {

    public final Property<Step> step = new Property<>(Step.STORAGE_MODE_CHOICE);
    public final Property<Boolean> gameRulesAreViewOnly = new Property<>(null);
    public final Property<GameRules> gameRules = new Property<>(null);
    private final Client client;
    private final BUPanelService buPanelService;
    private final AccountConfigurationService accountConfigurationService;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final LocalStorageSession.Factory localStorageSessionFactory;
    private StorageMode chosenStorageMode;
    private FirebaseRealtimeDatabase firebaseRealtimeDatabase;
    private GameRulesFirebaseObjectStorageAdapter gameRulesStoragePort;

    private SetupScreenViewModel(
        Client client,
        BUPanelService buPanelService,
        AccountConfigurationService accountConfigurationService,
        OkHttpClient httpClient,
        Gson gson,
        LocalStorageSession.Factory localStorageSessionFactory
    ) {
        this.client = client;
        this.buPanelService = buPanelService;
        this.accountConfigurationService = accountConfigurationService;
        this.httpClient = httpClient;
        this.gson = gson;
        this.localStorageSessionFactory = localStorageSessionFactory;
    }

    @Override
    public void close() throws Exception {
        if (gameRulesStoragePort != null) {
            gameRulesStoragePort.close();
            gameRulesStoragePort = null;
        }
        if (firebaseRealtimeDatabase != null) {
            firebaseRealtimeDatabase.close();
            firebaseRealtimeDatabase = null;
        }
    }

    public void onDontAskMeAgainButtonClick() {
        int result = JOptionPane.showConfirmDialog(
            null,
            "Setup will be skipped for this account.\n"
                + "You can still configure it later by reopening this panel.",
            "Skip setup for this account?",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        buPanelService.closePanel();
        accountConfigurationService.addCurrentAccountHashToAutoOpenConfigurationDisabled();
    }

    public void onStorageModeChosen(StorageMode storageMode) {
        chosenStorageMode = storageMode;
        if (storageMode == StorageMode.LOCAL) {
            handleLocalStorageChoice();
            return;
        }

        step.set(Step.REMOTE);
    }

    public CompletableFuture<Void> onRemoteStepFinished(FirebaseRealtimeDatabaseURL url) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // We also want to grab the game rules from the remote database, if they exist
        firebaseRealtimeDatabase = new FirebaseRealtimeDatabase(httpClient, gson, url);
        gameRulesStoragePort = new GameRulesFirebaseObjectStorageAdapter(
            firebaseRealtimeDatabase,
            gson
        );
        gameRulesStoragePort.read().whenComplete((gameRules, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            if (gameRules == null) {
                gameRulesAreViewOnly.set(false);
            } else {
                gameRulesAreViewOnly.set(true);
            }

            this.gameRules.set(gameRules);
            chosenStorageMode = StorageMode.FIREBASE;

            step.set(Step.GAME_RULES);
            future.complete(null);
        });

        return future;
    }

    public void onRemoteStepBack() {
        chosenStorageMode = null;
        step.set(Step.STORAGE_MODE_CHOICE);
    }

    public void onGameRulesStepBack() {
        if (chosenStorageMode == StorageMode.LOCAL) {
            step.set(Step.STORAGE_MODE_CHOICE);
            chosenStorageMode = null;
        } else {
            step.set(Step.REMOTE);
        }
        gameRulesAreViewOnly.set(null);
        gameRules.set(null);
    }

    public CompletableFuture<Void> onGameRulesStepFinish() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (chosenStorageMode == StorageMode.LOCAL) {
            finishLocalMode(future);
            return future;
        }

        if (gameRulesStoragePort == null) {
            Exception ex = new IllegalStateException("The Firebase URL is not set yet");
            future.completeExceptionally(ex);
            return future;
        }

        Boolean gameRulesAreViewOnlyValue = this.gameRulesAreViewOnly.get();
        if (gameRulesAreViewOnlyValue == null) {
            Exception ex = new IllegalStateException("Game rules are view only not set");
            future.completeExceptionally(ex);
            return future;
        }

        Runnable finalize = () -> {
            step.set(Step.STORAGE_MODE_CHOICE);
            gameRulesAreViewOnly.set(null);
            gameRules.set(null);
            chosenStorageMode = null;

            long accountHash = client.getAccountHash();
            AccountConfiguration accountConfiguration = AccountConfiguration.forFirebase(
                firebaseRealtimeDatabase.getDatabaseURL()
            );
            accountConfigurationService.setAccountConfiguration(accountConfiguration, accountHash);

            try {
                gameRulesStoragePort.close();
                gameRulesStoragePort = null;
            } catch (Exception ex) {
                future.completeExceptionally(ex);
                return;
            }

            try {
                firebaseRealtimeDatabase.close();
                firebaseRealtimeDatabase = null;
            } catch (Exception ex) {
                future.completeExceptionally(ex);
                return;
            }

            future.complete(null);
        };

        if (gameRulesAreViewOnlyValue) {
            finalize.run();
            return future;
        }

        GameRules gameRulesValue = this.gameRules.get();
        if (gameRulesValue == null) {
            Exception ex = new IllegalStateException("Game rules are not set");
            future.completeExceptionally(ex);
            return future;
        }

        gameRulesStoragePort.update(gameRulesValue).whenComplete((__, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            finalize.run();
        });

        return future;
    }

    private void finishLocalMode(CompletableFuture<Void> future) {
        GameRules gameRulesValue = gameRules.get();
        if (gameRulesValue == null) {
            future.completeExceptionally(new IllegalStateException("Game rules are not set"));
            return;
        }

        long accountHash = client.getAccountHash();
        LocalStorageSession localStorageSession = localStorageSessionFactory.create(accountHash);
        localStorageSession.getGameRulesStoragePort().update(gameRulesValue)
            .whenComplete((__, throwable) -> {
                try {
                    localStorageSession.close();
                } catch (Exception closeException) {
                    if (throwable == null) {
                        throwable = closeException;
                    } else {
                        throwable.addSuppressed(closeException);
                    }
                }

                if (throwable != null) {
                    future.completeExceptionally(throwable);
                    return;
                }

                accountConfigurationService.setAccountConfiguration(
                    AccountConfiguration.forLocal(accountHash),
                    accountHash
                );
                resetSetupState();
                future.complete(null);
            });
    }

    private void handleLocalStorageChoice() {
        long accountHash = client.getAccountHash();
        if (!LocalStorageSession.hasExistingProgress(accountHash)) {
            startFreshLocalSetup();
            return;
        }

        Object[] options = { "Continue Existing", "Start Fresh", "Cancel" };
        int result = JOptionPane.showOptionDialog(
            null,
            "Local progress already exists for this account.\n"
                + "Do you want to continue your existing local progress or start fresh and replace it?",
            "Local progress found",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        if (result == 0) {
            accountConfigurationService.setAccountConfiguration(
                AccountConfiguration.forLocal(accountHash),
                accountHash
            );
            resetSetupState();
            return;
        }

        if (result == 1) {
            try {
                LocalStorageSession.deleteExistingProgress(accountHash);
            } catch (IOException e) {
                log.error("Failed to clear existing local progress", e);
                JOptionPane.showMessageDialog(
                    null,
                    "Could not clear existing local progress. Please try again.",
                    "Error clearing local progress",
                    JOptionPane.ERROR_MESSAGE
                );
                chosenStorageMode = null;
                return;
            }

            startFreshLocalSetup();
            return;
        }

        chosenStorageMode = null;
    }

    private void startFreshLocalSetup() {
        gameRulesAreViewOnly.set(false);
        gameRules.set(
            GameRules.createWithDefaults(
                client.getAccountHash(),
                new ISOOffsetDateTime(OffsetDateTime.now())
            )
        );
        step.set(Step.GAME_RULES);
    }

    private void resetSetupState() {
        step.set(Step.STORAGE_MODE_CHOICE);
        gameRulesAreViewOnly.set(null);
        gameRules.set(null);
        chosenStorageMode = null;
    }

    public enum Step {
        STORAGE_MODE_CHOICE,
        REMOTE,
        GAME_RULES,
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        SetupScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private Client client;
        @Inject
        private BUPanelService buPanelService;
        @Inject
        private AccountConfigurationService accountConfigurationService;
        @Inject
        private OkHttpClient httpClient;
        @Inject
        private Gson gson;
        @Inject
        private LocalStorageSession.Factory localStorageSessionFactory;

        @Override
        public SetupScreenViewModel create() {
            return new SetupScreenViewModel(
                client,
                buPanelService,
                accountConfigurationService,
                httpClient,
                gson,
                localStorageSessionFactory
            );
        }
    }
}
