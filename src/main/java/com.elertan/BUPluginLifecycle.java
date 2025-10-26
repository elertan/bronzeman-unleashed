package com.elertan;

public interface BUPluginLifecycle {
    void startUp() throws Exception;
    void shutDown() throws Exception;
}
