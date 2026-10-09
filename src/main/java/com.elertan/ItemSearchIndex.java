package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;

/**
 * Name index of every item that can be unlocked, used to search items by name.
 * Built once on first use, in batches on the client thread so no single frame stalls.
 */
@Slf4j
@Singleton
public class ItemSearchIndex {

    private static final int BATCH_SIZE = 2_000;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ItemUnlockService itemUnlockService;

    private CompletableFuture<List<Entry>> entriesFuture;

    public synchronized CompletableFuture<List<Entry>> getEntries() {
        if (entriesFuture == null || entriesFuture.isCompletedExceptionally()) {
            entriesFuture = build();
        }
        return entriesFuture;
    }

    private CompletableFuture<List<Entry>> build() {
        CompletableFuture<List<Entry>> future = new CompletableFuture<>();
        List<Entry> entries = new ArrayList<>();
        int[] nextItemId = {0};
        long startedAt = System.currentTimeMillis();

        clientThread.invokeLater(() -> {
            try {
                int itemCount = client.getItemCount();
                int end = Math.min(nextItemId[0] + BATCH_SIZE, itemCount);
                for (int itemId = nextItemId[0]; itemId < end; itemId++) {
                    Entry entry = toEntry(itemId);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                nextItemId[0] = end;
                if (end < itemCount) {
                    // Not done yet, run again on a later frame
                    return false;
                }

                entries.sort((a, b) -> a.getLowerName().compareTo(b.getLowerName()));
                log.debug(
                    "Built item search index with {} items in {} ms",
                    entries.size(),
                    System.currentTimeMillis() - startedAt
                );
                future.complete(Collections.unmodifiableList(entries));
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
            return true;
        });

        return future;
    }

    private Entry toEntry(int itemId) {
        if (itemId <= 1 || ExcludedItemIds.IDS.contains(itemId)) {
            return null;
        }
        ItemComposition itemComposition = client.getItemDefinition(itemId);
        if (itemComposition.getPlaceholderTemplateId() != -1) {
            return null;
        }
        String name = itemComposition.getName();
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("null")
            || name.equals("Members object")) {
            return null;
        }

        // Keep only the canonical variant, so every entry is exactly what would be unlocked
        int canonicalItemId;
        try {
            canonicalItemId = itemUnlockService.canonicalizeItemId(itemId);
        } catch (Exception ex) {
            return null;
        }
        if (canonicalItemId != itemId) {
            return null;
        }

        return new Entry(itemId, name, name.toLowerCase(Locale.ROOT));
    }

    @Value
    public static class Entry {

        int id;
        String name;
        String lowerName;
    }
}
