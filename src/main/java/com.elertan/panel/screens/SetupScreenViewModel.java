package com.elertan.panel.screens;

import com.elertan.AccountConfigurationService;
import com.elertan.BUPanelService;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.GameRules;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.firebase.FirebaseObjectStorageAdapterBase;
import com.elertan.ui.Property;
import com.google.gson.Gson;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import okhttp3.OkHttpClient;

@Slf4j
public final class SetupScreenViewModel implements AutoCloseable {

    public final Property<Step> step = new Property<>(Step.REMOTE);
    public final Property<Boolean> gameRulesAreViewOnly = new Property<>(null);
    public final Property<GameRules> gameRules = new Property<>(null);
    private final Client client;
    private final BUPanelService buPanelService;
    private final AccountConfigurationService accountConfigurationService;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private FirebaseRealtimeDatabase firebaseRealtimeDatabase;
    private FirebaseObjectStorageAdapterBase<GameRules> gameRulesStoragePort;

    private SetupScreenViewModel(Client client, BUPanelService buPanelService,
        AccountConfigurationService accountConfigurationService, OkHttpClient httpClient, Gson gson) {
        this.client = client;
        this.buPanelService = buPanelService;
        this.accountConfigurationService = accountConfigurationService;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public void close() throws Exception {
        if (gameRulesStoragePort != null) { gameRulesStoragePort.close(); gameRulesStoragePort = null; }
        if (firebaseRealtimeDatabase != null) { firebaseRealtimeDatabase.close(); firebaseRealtimeDatabase = null; }
    }

    public void onDontAskMeAgainButtonClick() {
        int result = JOptionPane.showConfirmDialog(null,
            "We won't ask you again to set up bronzeman mode for this account.\n"
                + "You can set up bronzeman mode at any time by re-opening this panel.",
            "Confirm setup choice", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        buPanelService.closePanel();
        accountConfigurationService.addCurrentAccountHashToAutoOpenConfigurationDisabled();
    }

    public CompletableFuture<Void> onRemoteStepFinished(FirebaseRealtimeDatabaseURL url) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        firebaseRealtimeDatabase = new FirebaseRealtimeDatabase(httpClient, gson, url);
        gameRulesStoragePort = new FirebaseObjectStorageAdapterBase<>(
            "/GameRules", firebaseRealtimeDatabase, gson::toJsonTree, el -> gson.fromJson(el, GameRules.class));
        gameRulesStoragePort.read().whenComplete((rules, throwable) -> {
            if (throwable != null) { future.completeExceptionally(throwable); return; }
            gameRulesAreViewOnly.set(rules != null);
            this.gameRules.set(rules);
            step.set(Step.GAME_RULES);
            future.complete(null);
        });
        return future;
    }

    public void onGameRulesStepBack() {
        step.set(Step.REMOTE);
        gameRules.set(null);
    }

    public CompletableFuture<Void> onGameRulesStepFinish() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (gameRulesStoragePort == null) {
            future.completeExceptionally(new IllegalStateException("The Firebase URL is not set yet"));
            return future;
        }
        Boolean viewOnly = this.gameRulesAreViewOnly.get();
        if (viewOnly == null) {
            future.completeExceptionally(new IllegalStateException("Game rules are view only not set"));
            return future;
        }

        Runnable finalize = () -> {
            step.set(Step.REMOTE);
            gameRulesAreViewOnly.set(null);
            gameRules.set(null);
            accountConfigurationService.setAccountConfiguration(
                new AccountConfiguration(firebaseRealtimeDatabase.getDatabaseURL()),
                client.getAccountHash());
            try { gameRulesStoragePort.close(); gameRulesStoragePort = null; }
            catch (Exception ex) { future.completeExceptionally(ex); return; }
            try { firebaseRealtimeDatabase.close(); firebaseRealtimeDatabase = null; }
            catch (Exception ex) { future.completeExceptionally(ex); return; }
            future.complete(null);
        };

        if (viewOnly) { finalize.run(); return future; }

        GameRules rulesValue = this.gameRules.get();
        if (rulesValue == null) {
            future.completeExceptionally(new IllegalStateException("Game rules are not set"));
            return future;
        }
        gameRulesStoragePort.update(rulesValue).whenComplete((__, throwable) -> {
            if (throwable != null) { future.completeExceptionally(throwable); return; }
            finalize.run();
        });
        return future;
    }

    public enum Step { REMOTE, GAME_RULES }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        SetupScreenViewModel create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private Client client;
        @Inject private BUPanelService buPanelService;
        @Inject private AccountConfigurationService accountConfigurationService;
        @Inject private OkHttpClient httpClient;
        @Inject private Gson gson;

        @Override
        public SetupScreenViewModel create() {
            return new SetupScreenViewModel(client, buPanelService, accountConfigurationService, httpClient, gson);
        }
    }
}
