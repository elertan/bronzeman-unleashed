package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.utils.ListenerUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.AccountHashChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
@Singleton
public class AccountConfigurationService implements BUPluginLifecycle {

    private static final long INVALID_ACCOUNT_HASH = -1L;
    private static final Type AUTO_OPEN_ACCOUNT_CONFIGURATION_DISABLED_FOR_ACCOUNT_HASHES_TYPE = new TypeToken<List<Long>>() {
    }.getType();
    private static final Type ACCOUNT_CONFIGURATION_MAP_TYPE = new TypeToken<Map<Long, AccountConfiguration>>() {
    }.getType();
    private final ConcurrentLinkedQueue<Consumer<AccountConfiguration>> currentAccountConfigurationChangeListeners = new ConcurrentLinkedQueue<>();
    @Inject
    private Client client;
    @Inject
    private Gson gson;
    @Inject
    private BUPluginConfig buPluginConfig;
    @Inject
    private ConfigManager configManager;
    private List<Long> autoOpenAccountConfigurationDisabledForAccountHashes;
    private Map<Long, AccountConfiguration> accountConfigurationMap;
    private String lastStoredAccountConfigurationMapJson;
    private boolean isInitialCurrentAccountConfigurationDeterminedAfterAccountHash = true;
    private AccountConfiguration lastCurrentAccountConfiguration;

    @Override
    public void startUp() {
        initializeFromConfig();
    }

    @Override
    public void shutDown() {
        isInitialCurrentAccountConfigurationDeterminedAfterAccountHash = true;
        autoOpenAccountConfigurationDisabledForAccountHashes = null;
        accountConfigurationMap = null;
        lastStoredAccountConfigurationMapJson = null;
        lastCurrentAccountConfiguration = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!BUPluginConfig.GROUP.equals(event.getGroup())) {
            return;
        }

        if (BUPluginConfig.ACCOUNT_CONFIG_MAP_JSON_KEY.equals(event.getKey())) {
            final String newJson = buPluginConfig.accountConfigMapJson();
            if (!Objects.equals(newJson, lastStoredAccountConfigurationMapJson)) {
                initializeFromConfig();
            }
        }
    }

    public AccountConfiguration getAccountConfiguration(long accountHash) {
        ensureInitialized();
        if (accountHash == INVALID_ACCOUNT_HASH) {
            return null;
        }
        return accountConfigurationMap.get(accountHash);
    }

    public AccountConfiguration getCurrentAccountConfiguration() {
        return getAccountConfiguration(client.getAccountHash());
    }

    public void setCurrentAccountConfiguration(AccountConfiguration accountConfiguration) {
        setAccountConfiguration(accountConfiguration, client.getAccountHash());
    }

    public void setAccountConfiguration(AccountConfiguration accountConfiguration,
        long accountHash) {
        ensureInitialized();
        if (accountHash == INVALID_ACCOUNT_HASH) {
            throw new IllegalStateException("accountHash is invalid");
        }
        if (accountConfiguration == null) {
            accountConfigurationMap.remove(accountHash);
        } else {
            accountConfigurationMap.put(accountHash, accountConfiguration);
        }
        storeAccountConfigurationMap();
    }

    public void onAccountHashChanged(AccountHashChanged event) {
        AccountConfiguration accountConfiguration = getCurrentAccountConfiguration();
        if (!isInitialCurrentAccountConfigurationDeterminedAfterAccountHash && Objects.equals(
            accountConfiguration,
            lastCurrentAccountConfiguration
        )) {
            return;
        }
        isInitialCurrentAccountConfigurationDeterminedAfterAccountHash = false;
        lastCurrentAccountConfiguration = accountConfiguration;
        notifyCurrentAccountConfigurationChange(accountConfiguration);
    }

    public void addCurrentAccountConfigurationChangeListener(
        Consumer<AccountConfiguration> listener) {
        currentAccountConfigurationChangeListeners.add(listener);
    }

    public void removeCurrentAccountConfigurationChangeListener(
        Consumer<AccountConfiguration> listener) {
        currentAccountConfigurationChangeListeners.remove(listener);
    }

    public void addCurrentAccountHashToAutoOpenConfigurationDisabled()
        throws IllegalStateException {
        long accountHash = client.getAccountHash();
        if (accountHash == INVALID_ACCOUNT_HASH) {
            throw new IllegalStateException("accountHash is invalid");
        }
        if (autoOpenAccountConfigurationDisabledForAccountHashes == null) {
            throw new IllegalStateException(
                "autoOpenAccountConfigurationDisabledForAccountHashes is null");
        }
        autoOpenAccountConfigurationDisabledForAccountHashes.add(accountHash);
        storeAutoOpenAccountConfigurationDisabledForAccountHashes();
    }

    public boolean isCurrentAccountAutoOpenAccountConfigurationEnabled()
        throws IllegalStateException {
        long accountHash = client.getAccountHash();
        if (accountHash == INVALID_ACCOUNT_HASH) {
            throw new IllegalStateException("accountHash is invalid");
        }
        if (autoOpenAccountConfigurationDisabledForAccountHashes == null) {
            throw new IllegalStateException(
                "autoOpenAccountConfigurationDisabledForAccountHashes is null");
        }
        return !autoOpenAccountConfigurationDisabledForAccountHashes.contains(accountHash);
    }

    public boolean isReady() {
        return accountConfigurationMap != null;
    }

    public boolean isBronzemanEnabled() {
        return isReady() && getCurrentAccountConfiguration() != null;
    }

    public CompletableFuture<Void> waitUntilCurrentAccountConfigurationReady(
        Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            private Consumer<AccountConfiguration> listener;

            @Override
            public boolean isReady() {
                return accountConfigurationMap != null;
            }

            @Override
            public void addListener(Runnable notify) {
                listener = accountConfiguration -> notify.run();
                addCurrentAccountConfigurationChangeListener(listener);
            }

            @Override
            public void removeListener() {
                removeCurrentAccountConfigurationChangeListener(listener);
                listener = null;
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    private void notifyCurrentAccountConfigurationChange(
        AccountConfiguration accountConfiguration) {
        for (Consumer<AccountConfiguration> listener : currentAccountConfigurationChangeListeners) {
            try {
                listener.accept(accountConfiguration);
            } catch (Exception e) {
                // ignored
            }
        }
    }

    private void initializeFromConfig() {
        initializeAccountConfigMapFromConfig();
        initializeAutoOpenAccountConfigurationDisabledForAccountHashesFromConfig();
    }

    private void initializeAccountConfigMapFromConfig() {
        String json = buPluginConfig.accountConfigMapJson();
        if (json == null || json.isEmpty()) {
            setAccountConfigurationMap(new ConcurrentHashMap<>());
            lastStoredAccountConfigurationMapJson = null;
            return;
        }

        Map<Long, AccountConfiguration> parsed = gson.fromJson(
            json,
            ACCOUNT_CONFIGURATION_MAP_TYPE
        );
        if (parsed == null) {
            // Defensive: gson can return null for malformed or "null" input
            parsed = new ConcurrentHashMap<>();
        }
        setAccountConfigurationMap(new ConcurrentHashMap<>(parsed));
        lastStoredAccountConfigurationMapJson = json;
    }

    private void initializeAutoOpenAccountConfigurationDisabledForAccountHashesFromConfig() {
        String json = buPluginConfig.autoOpenAccountConfigurationDisabledForAccountHashesJson();
        if (json == null || json.isEmpty()) {
            autoOpenAccountConfigurationDisabledForAccountHashes = new ArrayList<>();
            return;
        }

        List<Long> parsed = gson.fromJson(
            json,
            AUTO_OPEN_ACCOUNT_CONFIGURATION_DISABLED_FOR_ACCOUNT_HASHES_TYPE
        );
        if (parsed == null) {
            parsed = new ArrayList<>();
        }
        autoOpenAccountConfigurationDisabledForAccountHashes = parsed;
    }

    private synchronized void setAccountConfigurationMap(
        ConcurrentHashMap<Long, AccountConfiguration> accountConfigurationMap) {
        this.accountConfigurationMap = accountConfigurationMap;

        AccountConfiguration accountConfiguration = getCurrentAccountConfiguration();
        if (Objects.equals(accountConfiguration, lastCurrentAccountConfiguration)) {
            return;
        }
        lastCurrentAccountConfiguration = accountConfiguration;
        notifyCurrentAccountConfigurationChange(accountConfiguration);
    }

    private synchronized void storeAccountConfigurationMap() {
        ensureInitialized();
        final String json = gson.toJson(accountConfigurationMap);
        // Only write if changed to avoid redundant config writes
        if (!Objects.equals(json, lastStoredAccountConfigurationMapJson)) {
            configManager.setConfiguration(
                BUPluginConfig.GROUP,
                BUPluginConfig.ACCOUNT_CONFIG_MAP_JSON_KEY, json
            );
            lastStoredAccountConfigurationMapJson = json;
        }
    }

    private synchronized void storeAutoOpenAccountConfigurationDisabledForAccountHashes() {
        ensureInitialized();
        String json = gson.toJson(autoOpenAccountConfigurationDisabledForAccountHashes);
        configManager.setConfiguration(
            BUPluginConfig.GROUP,
            BUPluginConfig.AUTO_OPEN_ACCOUNT_CONFIGURATION_DISABLED_FOR_ACCOUNT_HASHES_JSON_KEY,
            json
        );
    }

    private void ensureInitialized() {
        if (accountConfigurationMap == null) {
            throw new IllegalStateException(
                "accountConfigurationMap is not initialized. Call startUp() first.");
        }
    }
}
