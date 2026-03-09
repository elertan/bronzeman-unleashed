package com.elertan.remote.local;

import com.elertan.event.BUEvent;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.KeyListStoragePort;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.ObjectListStoragePort;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.StorageSession;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import javax.inject.Inject;
import javax.inject.Singleton;

public class LocalStorageSession implements StorageSession {

    private static final String RUNELITE_DIRECTORY = ".runelite";
    private static final String PLUGIN_DIRECTORY = "bronzeman-unleashed";

    private final KeyValueStoragePort<Long, Member> membersStoragePort;
    private final KeyValueStoragePort<Integer, UnlockedItem> unlockedItemsStoragePort;
    private final ObjectStoragePort<GameRules> gameRulesStoragePort;
    private final ObjectListStoragePort<BUEvent> lastEventStoragePort;
    private final KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByStoragePort;

    public LocalStorageSession(Gson gson, long accountHash) {
        Path accountStorageDirectory = getAccountStorageDir(accountHash);

        // Solo mode persists only durable personal progress. Group/event-oriented ports stay
        // in memory or no-op because those features are not supported locally.
        unlockedItemsStoragePort = new LocalStorageAdapters.JsonFileKeyValueStorageAdapter<>(
            accountStorageDirectory.resolve("UnlockedItems.json"),
            gson,
            Object::toString,
            Integer::valueOf,
            UnlockedItem.class
        );
        gameRulesStoragePort = new LocalStorageAdapters.JsonFileObjectStorageAdapter<>(
            accountStorageDirectory.resolve("GameRules.json"),
            gson,
            GameRules.class
        );
        membersStoragePort = new LocalStorageAdapters.InMemoryKeyValueStorageAdapter<>();
        groundItemOwnedByStoragePort = new LocalStorageAdapters.InMemoryKeyListStorageAdapter<>();
        lastEventStoragePort = new NoOpAdapters.NoOpObjectListStorageAdapter<>();
    }

    public static Path getAccountStorageDir(long accountHash) {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isEmpty()) {
            userHome = ".";
        }

        return Paths.get(
            userHome,
            RUNELITE_DIRECTORY,
            PLUGIN_DIRECTORY,
            String.valueOf(accountHash)
        );
    }

    public static boolean hasExistingProgress(long accountHash) {
        Path accountStorageDirectory = getAccountStorageDir(accountHash);
        return Files.exists(accountStorageDirectory.resolve("UnlockedItems.json"))
            || Files.exists(accountStorageDirectory.resolve("GameRules.json"));
    }

    public static void deleteExistingProgress(long accountHash) throws IOException {
        Path accountStorageDirectory = getAccountStorageDir(accountHash);
        if (!Files.exists(accountStorageDirectory)) {
            return;
        }

        try (java.util.stream.Stream<Path> stream = Files.walk(accountStorageDirectory)) {
            stream
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public KeyValueStoragePort<Long, Member> getMembersStoragePort() {
        return membersStoragePort;
    }

    @Override
    public KeyValueStoragePort<Integer, UnlockedItem> getUnlockedItemsStoragePort() {
        return unlockedItemsStoragePort;
    }

    @Override
    public ObjectStoragePort<GameRules> getGameRulesStoragePort() {
        return gameRulesStoragePort;
    }

    @Override
    public ObjectListStoragePort<BUEvent> getLastEventStoragePort() {
        return lastEventStoragePort;
    }

    @Override
    public KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> getGroundItemOwnedByStoragePort() {
        return groundItemOwnedByStoragePort;
    }

    @Override
    public void close() throws Exception {
        groundItemOwnedByStoragePort.close();
        lastEventStoragePort.close();
        membersStoragePort.close();
        unlockedItemsStoragePort.close();
        gameRulesStoragePort.close();
    }

    @Singleton
    public static final class Factory {

        private final Gson gson;

        @Inject
        public Factory(Gson gson) {
            this.gson = gson;
        }

        public LocalStorageSession create(long accountHash) {
            return new LocalStorageSession(gson, accountHash);
        }
    }
}
