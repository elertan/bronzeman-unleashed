package com.elertan.panel.screens.setup;

import com.elertan.models.AccountConfiguration.StorageMode;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public final class StorageModeStepViewModel {

    private final Listener listener;

    private StorageModeStepViewModel(Listener listener) {
        this.listener = listener;
    }

    public void onPlaySoloClicked() {
        listener.onStorageModeChosen(StorageMode.LOCAL);
    }

    public void onPlayWithGroupClicked() {
        listener.onStorageModeChosen(StorageMode.FIREBASE);
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        StorageModeStepViewModel create(Listener listener);
    }

    public interface Listener {

        void onStorageModeChosen(StorageMode storageMode);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        public FactoryImpl() {
        }

        @Override
        public StorageModeStepViewModel create(Listener listener) {
            return new StorageModeStepViewModel(listener);
        }
    }
}
