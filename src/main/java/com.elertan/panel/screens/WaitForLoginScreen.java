package com.elertan.panel.screens;

import com.elertan.BUResourceService;
import com.elertan.panel.components.LoadingPanel;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class WaitForLoginScreen extends LoadingPanel {

    private WaitForLoginScreen(BUResourceService buResourceService) {
        super(buResourceService, "Waiting for account login...");
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        WaitForLoginScreen create();
    }

    @Singleton
    private static class FactoryImpl implements Factory {
        @Inject private BUResourceService buResourceService;

        @Override
        public WaitForLoginScreen create() {
            return new WaitForLoginScreen(buResourceService);
        }
    }
}
