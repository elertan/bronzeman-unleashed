package com.elertan.panel.screens.main.unlockedItems;

import com.elertan.BUResourceService;
import com.elertan.panel.components.LoadingPanel;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class LoadingScreen extends LoadingPanel {

    private LoadingScreen(BUResourceService buResourceService) {
        super(buResourceService, "Loading...");
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        LoadingScreen create();
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private BUResourceService buResourceService;

        @Override
        public LoadingScreen create() {
            return new LoadingScreen(buResourceService);
        }
    }
}
