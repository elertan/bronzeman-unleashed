package com.elertan;

import com.elertan.models.AccountConfiguration;
import com.elertan.utils.Observable;
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
    private static final Type AUTO_OPEN_DISABLED_HASHES_TYPE = new TypeToken<List<Long>>() {}.getType();
    private static final Type ACCOUNT_CONFIG_MAP_TYPE = new TypeToken<Map<Long, AccountConfiguration>>() {}.getType();

    private final Observable<AccountConfiguration> currentAccountConfiguration = Observable.empty();
    @Inject private Client client;
    @Inject private Gson gson;
    @Inject private BUPluginConfig buPluginConfig;
    @Inject private ConfigManager configManager;

    private List<Long> autoOpenDisabledHashes;
    private Map<Long, AccountConfiguration> accountConfigurationMap;
    private String lastStoredMapJson;
    private boolean isInitialCurrentAccountConfigurationDeterminedAfterAccountHash = true;
    private AccountConfiguration lastCurrentAccountConfiguration;

    @Override
    public void startUp() { initializeFromConfig(); }

    @Override
    public void shutDown() {
        isInitialCurrentAccountConfigurationDeterminedAfterAccountHash = true;
        autoOpenDisabledHashes = null;
        accountConfigurationMap = null;
        lastStoredMapJson = null;
        lastCurrentAccountConfiguration = null;
        currentAccountConfiguration.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!BUPluginConfig.GROUP.equals(event.getGroup())) return;
        if (BUPluginConfig.ACCOUNT_CONFIG_MAP_JSON_KEY.equals(event.getKey())) {
            String newJson = buPluginConfig.accountConfigMapJson();
            if (!Objects.equals(newJson, lastStoredMapJson)) initializeFromConfig();
        }
    }

    public AccountConfiguration getAccountConfiguration(long accountHash) {
        ensureInitialized();
        return accountHash == INVALID_ACCOUNT_HASH ? null : accountConfigurationMap.get(accountHash);
    }

    // Requires: client thread
    public AccountConfiguration getCurrentAccountConfiguration() {
        return getAccountConfiguration(client.getAccountHash());
    }

    // Requires: client thread
    public void setCurrentAccountConfiguration(AccountConfiguration accountConfiguration) {
        setAccountConfiguration(accountConfiguration, client.getAccountHash());
    }

    public void setAccountConfiguration(AccountConfiguration accountConfiguration, long accountHash) {
        ensureInitialized();
        if (accountHash == INVALID_ACCOUNT_HASH) throw new IllegalStateException("accountHash is invalid");
        if (accountConfiguration == null) accountConfigurationMap.remove(accountHash);
        else accountConfigurationMap.put(accountHash, accountConfiguration);
        storeAccountConfigurationMap();
    }

    public void onAccountHashChanged(AccountHashChanged event) {
        AccountConfiguration config = getCurrentAccountConfiguration();
        if (!isInitialCurrentAccountConfigurationDeterminedAfterAccountHash
            && Objects.equals(config, lastCurrentAccountConfiguration)) return;
        isInitialCurrentAccountConfigurationDeterminedAfterAccountHash = false;
        lastCurrentAccountConfiguration = config;
        currentAccountConfiguration.set(config);
    }

    public Observable<AccountConfiguration> currentAccountConfiguration() {
        return currentAccountConfiguration;
    }

    // Requires: client thread
    public void addCurrentAccountHashToAutoOpenConfigurationDisabled() throws IllegalStateException {
        long accountHash = client.getAccountHash();
        if (accountHash == INVALID_ACCOUNT_HASH) throw new IllegalStateException("accountHash is invalid");
        if (autoOpenDisabledHashes == null) throw new IllegalStateException("autoOpenDisabledHashes is null");
        autoOpenDisabledHashes.add(accountHash);
        storeAutoOpenDisabledHashes();
    }

    // Requires: client thread
    public boolean isCurrentAccountAutoOpenAccountConfigurationEnabled() throws IllegalStateException {
        long accountHash = client.getAccountHash();
        if (accountHash == INVALID_ACCOUNT_HASH) throw new IllegalStateException("accountHash is invalid");
        if (autoOpenDisabledHashes == null) throw new IllegalStateException("autoOpenDisabledHashes is null");
        return !autoOpenDisabledHashes.contains(accountHash);
    }

    public boolean isReady() { return accountConfigurationMap != null; }

    public boolean isBronzemanEnabled() { return isReady() && getCurrentAccountConfiguration() != null; }

    public CompletableFuture<AccountConfiguration> waitUntilCurrentAccountConfigurationReady(Duration timeout) {
        return currentAccountConfiguration.await(timeout);
    }

    private void initializeFromConfig() {
        // Account configuration map
        String mapJson = buPluginConfig.accountConfigMapJson();
        if (mapJson == null || mapJson.isEmpty()) {
            setAccountConfigurationMap(new ConcurrentHashMap<>());
            lastStoredMapJson = null;
        } else {
            Map<Long, AccountConfiguration> parsed = gson.fromJson(mapJson, ACCOUNT_CONFIG_MAP_TYPE);
            setAccountConfigurationMap(new ConcurrentHashMap<>(parsed != null ? parsed : Map.of()));
            lastStoredMapJson = mapJson;
        }
        // Auto-open disabled hashes
        String hashesJson = buPluginConfig.autoOpenAccountConfigurationDisabledForAccountHashesJson();
        if (hashesJson == null || hashesJson.isEmpty()) {
            autoOpenDisabledHashes = new ArrayList<>();
        } else {
            List<Long> parsed = gson.fromJson(hashesJson, AUTO_OPEN_DISABLED_HASHES_TYPE);
            autoOpenDisabledHashes = parsed != null ? parsed : new ArrayList<>();
        }
    }

    private synchronized void setAccountConfigurationMap(ConcurrentHashMap<Long, AccountConfiguration> map) {
        this.accountConfigurationMap = map;
        AccountConfiguration config = getCurrentAccountConfiguration();
        if (Objects.equals(config, lastCurrentAccountConfiguration)) return;
        lastCurrentAccountConfiguration = config;
        currentAccountConfiguration.set(config);
    }

    private synchronized void storeAccountConfigurationMap() {
        ensureInitialized();
        String json = gson.toJson(accountConfigurationMap);
        if (!Objects.equals(json, lastStoredMapJson)) {
            configManager.setConfiguration(BUPluginConfig.GROUP, BUPluginConfig.ACCOUNT_CONFIG_MAP_JSON_KEY, json);
            lastStoredMapJson = json;
        }
    }

    private synchronized void storeAutoOpenDisabledHashes() {
        ensureInitialized();
        String json = gson.toJson(autoOpenDisabledHashes);
        configManager.setConfiguration(BUPluginConfig.GROUP,
            BUPluginConfig.AUTO_OPEN_ACCOUNT_CONFIGURATION_DISABLED_FOR_ACCOUNT_HASHES_JSON_KEY, json);
    }

    private void ensureInitialized() {
        if (accountConfigurationMap == null) {
            throw new IllegalStateException("accountConfigurationMap is not initialized. Call startUp() first.");
        }
    }
}
