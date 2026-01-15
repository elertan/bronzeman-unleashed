/* BSD 2-Clause License

Copyright (c) 2022, Jake Barter
All rights reserved.

Copyright (c) 2022, pajlads

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.elertan.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

// Code from: RuneProfile Plugin
// Repository: https://github.com/ReinhardtR/runeprofile-plugin
// License: BSD 2-Clause License
/**
 * This thread-safe singleton holds a mapping of item names to their item id, using the RuneLite API.
 * <p>
 * Unlike {@link net.runelite.client.game.ItemManager#search(String)}, this mapping supports untradable items.
 */
@Slf4j
@Singleton
public class ItemSearcher {
    public static final String ITEM_CACHE_BASE_URL = "https://static.runelite.net/cache/item/";
    private final Map<String, Integer> itemIdByName = Collections.synchronizedMap(new HashMap<>(16384));
    private @Inject OkHttpClient httpClient;
    private @Inject Gson gson;

    /**
     * @param name the exact in-game name of an item
     * @return the id associated with the item name, or null if not found
     */
    @Nullable
    public Integer findItemId(@NonNull String name) {
        return itemIdByName.get(name);
    }

    /**
     * Begins the initialization process for {@link #itemIdByName}
     * by querying item names and noted item ids from the RuneLite API,
     * before passing them to {@link #populate(Map, Set)}
     *
     * @implNote This operation does not block the current thread,
     * by utilizing OkHttp's thread pool and Java's Fork-Join common pool.
     */
    @Inject
    void init() {
        queryNamesById()
            .thenAcceptBothAsync(
                queryNotedItemIds().exceptionally(e -> {
                    log.error("Failed to read noted items", e);
                    return Collections.emptySet();
                }),
                this::populate
            )
            .exceptionally(e -> {
                log.error("Failed to read item names", e);
                return null;
            });
    }

    /**
     * Populates {@link #itemIdByName} with the inverted mappings of {@code namesById},
     * while skipping noted items specified in {@code notedIds}.
     *
     * @param namesById a mapping of item id's to the corresponding in-game name
     * @param notedIds  the id's of noted items
     * @implNote When multiple non-noted item id's have the same in-game name, only the earliest id is saved
     */
    void populate(@NonNull Map<Integer, String> namesById, @NonNull Set<Integer> notedIds) {
        namesById.forEach((id, name) -> {
            if (!notedIds.contains(id))
                itemIdByName.putIfAbsent(name, id);
        });

        log.debug("Completed initialization of item cache with {} entries", itemIdByName.size());
    }

    /**
     * @return a mapping of item ids to their in-game names, provided by the RuneLite API
     */
    private CompletableFuture<Map<Integer, String>> queryNamesById() {
        return queryCache("names.json", new TypeToken<Map<Integer, String>>() {
        });
    }

    /**
     * @return a set of id's of noted items, provided by the RuneLite API
     */
    private CompletableFuture<Set<Integer>> queryNotedItemIds() {
        return queryCache("notes.json", new TypeToken<Map<Integer, Integer>>() {
        })
            .thenApply(Map::keySet);
    }

    /**
     * @param fileName the name of the file to query from RuneLite's cache
     * @param type     a type token that indicates how the json response should be parsed
     * @return the transformed cache response, wrapped in a future
     */
    private <T> CompletableFuture<T> queryCache(@NonNull String fileName, @NonNull TypeToken<T> type) {
        return readJson(httpClient, gson, ITEM_CACHE_BASE_URL + fileName, type);
    }

    // TODO: Use other code instead of copy-pasta from RuneProfile?
    private <T> CompletableFuture<T> readJson(@NonNull OkHttpClient httpClient, @NonNull Gson gson, @NonNull String url, @NonNull TypeToken<T> type) {
        return readUrl(httpClient, url, reader -> gson.fromJson(reader, type.getType()));
    }

    // TODO: Use other code instead of copy-pasta from RuneProfile?
    private <T> CompletableFuture<T> readUrl(@NonNull OkHttpClient httpClient, @NonNull String url, @NonNull Function<Reader, T> transformer) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                assert response.body() != null;
                try (Reader reader = response.body().charStream()) {
                    future.complete(transformer.apply(reader));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    response.close();
                }
            }
        });
        return future;
    }
}
