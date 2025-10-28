package com.elertan.panel2.screens;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPanelService;
import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.panel2.screens.setup.RemoteStepViewViewModel;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.firebase.storageAdapters.GameRulesFirebaseObjectStorageAdapter;
import com.elertan.ui.Property;
import com.google.gson.Gson;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import okhttp3.OkHttpClient;

import javax.swing.*;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class SetupScreenViewModel implements AutoCloseable {
    public enum Step {
        REMOTE,
        GAME_RULES,
    }

    public final Property<Step> step = new Property<>(Step.REMOTE);
    public final Property<GameRules> gameRules = new Property<>(null);

    @Inject
    private Client client;
    @Inject
    private BUPanelService buPanelService;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private RemoteStepViewViewModel.Factory remoteStepViewViewModelFactory;
    @Inject
    private OkHttpClient httpClient;
    @Inject
    private Gson gson;

    @Override
    public void close() throws Exception {

    }

    public void onDontAskMeAgainButtonClick() {
        int result = JOptionPane.showConfirmDialog(
                null,
                "We won't ask you again to set up bronzeman mode for this account.\n"
                        + "You can set up bronzeman mode at any time by re-opening this panel.",
                "Confirm setup choice",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        buPanelService.closePanel();
        accountConfigurationService.addCurrentAccountHashToAutoOpenConfigurationDisabled();
    }

    public CompletableFuture<Void> onRemoteStepFinished(FirebaseRealtimeDatabaseURL url) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // We also want to grab the game rules from the remote database, if they exist
        FirebaseRealtimeDatabase db = new FirebaseRealtimeDatabase(httpClient, gson, url);
        GameRulesFirebaseObjectStorageAdapter storagePort = new GameRulesFirebaseObjectStorageAdapter(db, gson);
        storagePort.read().whenComplete((gameRules, throwable) -> {
            if (throwable != null)  {
                future.completeExceptionally(throwable);
                return;
            }

            this.gameRules.set(gameRules);

            step.set(Step.GAME_RULES);
            future.complete(null);
        });

        return future;
    }

    public void onGameRulesStepBack() {
        step.set(Step.REMOTE);
        gameRules.set(null);
    }

    public void onGameRulesStepFinish() {
        // TODO: Save the game rules to the db etc
    }

}
