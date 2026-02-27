package com.elertan.panel.screens.setup;

import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Setter;
import okhttp3.OkHttpClient;

public final class RemoteStepViewViewModel implements AutoCloseable {

    public final Property<StateView> stateView = new Property<>(StateView.ENTRY);
    private final AtomicReference<TrySubmitAttempt> trySubmitAttempt = new AtomicReference<>(null);
    private final OkHttpClient httpClient;
    private final Listener listener;

    private RemoteStepViewViewModel(OkHttpClient httpClient, Listener listener) {
        this.httpClient = httpClient;
        this.listener = listener;
    }

    @Override
    public void close() throws Exception {
        TrySubmitAttempt attempt = trySubmitAttempt.getAndSet(null);
        if (attempt != null) attempt.cancel();
    }

    public CompletableFuture<String> onEntryViewTrySubmit(FirebaseRealtimeDatabaseURL url) {
        stateView.set(StateView.CHECKING);
        CompletableFuture<String> future = new CompletableFuture<>();

        TrySubmitAttempt prev = trySubmitAttempt.get();
        if (prev != null) { prev.cancel(); trySubmitAttempt.set(null); }

        TrySubmitAttempt attempt = new TrySubmitAttempt();
        attempt.setFuture(future);
        trySubmitAttempt.set(attempt);

        future.whenComplete((__, throwable) -> {
            trySubmitAttempt.set(null);
            stateView.set(StateView.ENTRY);
        });

        CompletableFuture<Boolean> canConnectFuture = FirebaseRealtimeDatabase.canConnectTo(httpClient, url);
        attempt.setCanConnectFuture(canConnectFuture);

        canConnectFuture.whenComplete((canConnect, throwable) -> {
            if (throwable != null) { future.completeExceptionally(throwable); return; }
            if (!canConnect) {
                future.complete("Could not connect to the Firebase Realtime database, please check the URL or try again later.");
                return;
            }
            CompletableFuture<Void> finishedFuture = listener.onRemoteStepFinished(url);
            attempt.setOnFinishedFuture(finishedFuture);
            finishedFuture.whenComplete((__, t2) -> {
                if (t2 != null) future.completeExceptionally(t2);
                else future.complete(null);
            });
        });
        return future;
    }

    public void onCancelChecking() {
        TrySubmitAttempt attempt = trySubmitAttempt.getAndSet(null);
        if (attempt != null) attempt.cancel();
        stateView.set(StateView.ENTRY);
    }

    public enum StateView { ENTRY, CHECKING }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        RemoteStepViewViewModel create(Listener listener);
    }

    public interface Listener {
        CompletableFuture<Void> onRemoteStepFinished(FirebaseRealtimeDatabaseURL url);
    }

    @Singleton
    private static class FactoryImpl implements Factory {
        private final OkHttpClient httpClient;
        @Inject public FactoryImpl(OkHttpClient httpClient) { this.httpClient = httpClient; }

        @Override
        public RemoteStepViewViewModel create(Listener listener) {
            return new RemoteStepViewViewModel(httpClient, listener);
        }
    }

    private static final class TrySubmitAttempt {
        @Setter private CompletableFuture<String> future;
        @Setter private CompletableFuture<Boolean> canConnectFuture;
        @Setter private CompletableFuture<Void> onFinishedFuture;

        void cancel() {
            if (future != null) future.cancel(true);
            if (canConnectFuture != null) canConnectFuture.cancel(true);
            if (onFinishedFuture != null) onFinishedFuture.cancel(true);
        }
    }
}
