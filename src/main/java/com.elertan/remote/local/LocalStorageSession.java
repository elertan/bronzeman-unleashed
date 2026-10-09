package com.elertan.remote.local;

import com.elertan.event.BUEvent;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.StartingItems;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.KeyListStoragePort;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.ObjectListStoragePort;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.StorageSession;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;

public class LocalStorageSession implements StorageSession {

    private static final String PLUGIN_DIRECTORY = "bronzeman-unleashed";
    private static final String STARTING_ITEMS_FILE = "StartingItems.json";

    private final KeyValueStoragePort<Long, Member> membersStoragePort;
    private final KeyValueStoragePort<Integer, UnlockedItem> unlockedItemsStoragePort;
    private final ObjectStoragePort<GameRules> gameRulesStoragePort;
    private final ObjectListStoragePort<BUEvent> lastEventStoragePort;
    private final KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByStoragePort;
    private final KeyValueStoragePort<Long, StartingItems> startingItemsStoragePort;

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
        startingItemsStoragePort = new LocalStorageAdapters.JsonFileKeyValueStorageAdapter<>(
            accountStorageDirectory.resolve(STARTING_ITEMS_FILE),
            gson,
            Object::toString,
            Long::valueOf,
            StartingItems.class
        );
        membersStoragePort = new LocalStorageAdapters.InMemoryKeyValueStorageAdapter<>();
        groundItemOwnedByStoragePort = new LocalStorageAdapters.InMemoryKeyListStorageAdapter<>();
        lastEventStoragePort = new NoOpAdapters.NoOpObjectListStorageAdapter<>();
    }

    public static Path getAccountStorageDir(long accountHash) {
        return RuneLite.RUNELITE_DIR.toPath()
            .resolve(PLUGIN_DIRECTORY)
            .resolve(String.valueOf(accountHash));
    }

    public static boolean hasExistingProgress(long accountHash) {
        Path accountStorageDirectory = getAccountStorageDir(accountHash);
        return Files.exists(accountStorageDirectory.resolve("UnlockedItems.json"))
            || Files.exists(accountStorageDirectory.resolve("GameRules.json"));
    }

    // Local mode has no member records, so a saved starting items file is how a later setup
    // knows this is an existing account.
    public static boolean hasStartingItems(long accountHash) {
        Path file = getAccountStorageDir(accountHash).resolve(STARTING_ITEMS_FILE);
        if (!Files.exists(file)) {
            return false;
        }
        // Deleting a key keeps the file ("{}"), so check that a record is still in it.
        try {
            JsonElement json = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            return json.isJsonObject() && json.getAsJsonObject().size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void deleteExistingProgress(long accountHash) throws IOException {
        Path accountStorageDirectory = getAccountStorageDir(accountHash);
        if (!Files.exists(accountStorageDirectory)) {
            return;
        }

        List<Path> pathsToDelete;
        try (java.util.stream.Stream<Path> stream = Files.walk(accountStorageDirectory)) {
            // Delete children before parents so directory removal does not fail while still populated.
            pathsToDelete = stream
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        }

        for (Path path : pathsToDelete) {
            Files.deleteIfExists(path);
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
    public KeyValueStoragePort<Long, StartingItems> getStartingItemsStoragePort() {
        return startingItemsStoragePort;
    }

    @Override
    public void close() throws Exception {
        startingItemsStoragePort.close();
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
