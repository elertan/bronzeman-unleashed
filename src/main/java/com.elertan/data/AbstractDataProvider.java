package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractDataProvider implements BUPluginLifecycle {

    @Getter
    private final Observable<State> state = Observable.of(State.NotReady);
    private Subscription remoteStorageSubscription;

    protected abstract RemoteStorageService getRemoteStorageService();
    protected abstract void onRemoteStorageReady();
    protected abstract void onRemoteStorageNotReady();

    @Override
    public void startUp() throws Exception {
        remoteStorageSubscription = getRemoteStorageService().getState().subscribeImmediate(
            (remoteState, old) -> onRemoteStorageStateChanged(remoteState));
    }

    @Override
    public void shutDown() throws Exception {
        if (remoteStorageSubscription != null) { remoteStorageSubscription.dispose(); remoteStorageSubscription = null; }
        onRemoteStorageNotReady();
        state.set(State.NotReady);
    }

    public CompletableFuture<State> await(Duration timeout) {
        return Observable.awaitValue(state, State.Ready, timeout);
    }

    protected void setState(State newState) { state.set(newState); }

    protected static <L> void notifyListeners(Collection<L> listeners, Consumer<L> action) {
        for (L listener : listeners) {
            try { action.accept(listener); } catch (Exception e) { log.error("Listener notification error", e); }
        }
    }

    private void onRemoteStorageStateChanged(RemoteStorageService.State remoteState) {
        if (remoteState == RemoteStorageService.State.NotReady) {
            onRemoteStorageNotReady();
            setState(State.NotReady);
        } else if (remoteState == RemoteStorageService.State.Ready) {
            onRemoteStorageReady();
        }
    }

    public enum State { NotReady, Ready }
}
