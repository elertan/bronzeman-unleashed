package com.elertan.remote.firebase;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

@Slf4j
public class FirebaseRealtimeDatabase implements AutoCloseable {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse(
        "application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Getter
    private final FirebaseRealtimeDatabaseURL databaseURL;
    @Getter
    private final FirebaseSSEStream stream;

    public FirebaseRealtimeDatabase(OkHttpClient httpClient, Gson gson,
        FirebaseRealtimeDatabaseURL databaseURL) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.databaseURL = databaseURL;
        this.stream = new FirebaseSSEStream(httpClient, gson, databaseURL);
    }

    public static CompletableFuture<Boolean> canConnectTo(OkHttpClient httpClient,
        FirebaseRealtimeDatabaseURL url) {
        final String strUrl = url.getBaseUrl() + "/__BRONZEMAN_UNLEASED_CAN_CONNECT_TEST.json";
        Request request = FirebaseRealtimeDatabase.getRequestBuilder(strUrl).get().build();

        return enqueueAsync(httpClient, request).handle((response, error) -> {
            if (error != null) {
                log.error("FirebaseRealtimeDatabase canConnect() exception: ", error);
                return Boolean.FALSE;
            }
            try (okhttp3.Response res = response) {
                return res.isSuccessful();
            }
        });
    }

    public static Request.Builder getRequestBuilder(String url) {
        return new Request.Builder().url(url)
            .header("User-Agent", "BronzemanUnleashedPlugin");
    }

    /**
     * Validates that a base path is a single-level resource path (e.g., "/Resource").
     * @throws IllegalArgumentException if path is null, doesn't start with '/', or has multiple levels
     */
    public static void validateBasePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        if (path.lastIndexOf("/") != 0) {
            throw new IllegalArgumentException("path must only be a starting resource");
        }
    }

    private static CompletableFuture<okhttp3.Response> enqueueAsync(OkHttpClient client,
        Request request) {
        final okhttp3.Call call = client.newCall(request);
        final CompletableFuture<okhttp3.Response> future = new CompletableFuture<>();
        future.whenComplete((r, t) -> {
            if (future.isCancelled()) {
                call.cancel();
            }
        });
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onResponse(okhttp3.Call c, okhttp3.Response response) {
                if (!future.isDone()) {
                    future.complete(response);
                } else {
                    response.close();
                }
            }
        });
        return future;
    }

    @Override
    public void close() throws Exception {
        FirebaseSSEStream stream = getStream();
        stream.stop();
    }

    public CompletableFuture<JsonElement> get(String path) {
        String url = getUrlForPath(path);
        Request request = getRequestBuilder(url)
            .get()
            .build();
        return executeJsonRequest(request);
    }

    public CompletableFuture<JsonElement> post(String path, JsonElement data) {
        return executeJsonRequest(buildJsonRequestWithBody(path, "POST", data));
    }

    public CompletableFuture<JsonElement> put(String path, JsonElement data) {
        return executeJsonRequest(buildJsonRequestWithBody(path, "PUT", data));
    }

    public CompletableFuture<Void> delete(String path) {
        String url = getUrlForPath(path);
        Request request = getRequestBuilder(url)
            .delete()
            .build();
        return executeVoidRequest(request);
    }

    private CompletableFuture<JsonElement> executeJsonRequest(Request request) {
        final long startNanos = System.nanoTime();
        final CompletableFuture<JsonElement> future = new CompletableFuture<>();
        final okhttp3.Call call = httpClient.newCall(request);

        future.whenComplete((r, t) -> {
            if (future.isCancelled()) {
                call.cancel();
            }
        });

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                if (!future.isDone()) {
                    log.error(
                        "{} {} failed after {} ms",
                        request.method(),
                        request.url(),
                        (System.nanoTime() - startNanos) / 1_000_000
                    );
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onResponse(okhttp3.Call c, okhttp3.Response response) {
                try (okhttp3.Response res = response) {
                    if (!res.isSuccessful()) {
                        String snippet = null;
                        ResponseBody errBody = res.body();
                        if (errBody != null) {
                            try {
                                snippet = errBody.string();
                            } catch (IOException ignore) { /* ignore */ }
                        }
                        String msg = String.format(
                            "%s %s -> HTTP %d %s%s",
                            request.method(), request.url(), res.code(), res.message(),
                            snippet != null ? ": " + snippet : ""
                        );
                        if (!future.isDone()) {
                            log.error(msg);
                            future.completeExceptionally(new IOException(msg));
                        }
                        return;
                    }

                    ResponseBody body = res.body();
                    if (body == null) {
                        String msg = String.format(
                            "%s %s -> empty body",
                            request.method(),
                            request.url()
                        );
                        if (!future.isDone()) {
                            log.error(msg);
                            future.completeExceptionally(new IOException(msg));
                        }
                        return;
                    }

                    try (Reader reader = body.charStream()) {
                        JsonElement jsonElement = gson.fromJson(reader, JsonElement.class);
                        if (!future.isDone()) {
                            future.complete(jsonElement);
                        }
                    } catch (Exception parseErr) {
                        if (!future.isDone()) {
                            future.completeExceptionally(parseErr);
                        }
                    }
                }
            }
        });

        return future;
    }

    private CompletableFuture<Void> executeVoidRequest(Request request) {
        final long startNanos = System.nanoTime();
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final okhttp3.Call call = httpClient.newCall(request);

        future.whenComplete((r, t) -> {
            if (future.isCancelled()) {
                call.cancel();
            }
        });

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                if (!future.isDone()) {
                    log.error(
                        "{} {} failed after {} ms",
                        request.method(),
                        request.url(),
                        (System.nanoTime() - startNanos) / 1_000_000
                    );
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onResponse(okhttp3.Call c, okhttp3.Response response) {
                try (okhttp3.Response res = response) {
                    if (!res.isSuccessful()) {
                        String snippet = null;
                        ResponseBody errBody = res.body();
                        if (errBody != null) {
                            try {
                                snippet = errBody.string();
                            } catch (IOException ignore) { /* ignore */ }
                        }
                        String msg = String.format(
                            "%s %s -> HTTP %d %s%s",
                            request.method(), request.url(), res.code(), res.message(),
                            snippet != null ? ": " + snippet : ""
                        );
                        if (!future.isDone()) {
                            log.error(msg);
                            future.completeExceptionally(new IOException(msg));
                        }
                        return;
                    }

                    // Success: nothing to parse, just complete
                    if (!future.isDone()) {
                        future.complete(null);
                    }
                }
            }
        });

        return future;
    }

    private Request buildJsonRequestWithBody(String path, String method, JsonElement data) {
        String url = getUrlForPath(path);
        String jsonPayload = gson.toJson(data);
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, jsonPayload);
        return getRequestBuilder(url)
            .header("Content-Type", "application/json")
            .method(method, body)
            .build();
    }

    private String getUrlForPath(String path) {
        HttpUrl base = HttpUrl.parse(databaseURL.getBaseUrl());
        if (base == null) {
            throw new IllegalArgumentException("Invalid base URL: " + databaseURL.getBaseUrl());
        }

        String rawPath = path;
        String rawQuery = null;
        int q = path.indexOf('?');
        if (q >= 0) {
            rawPath = path.substring(0, q);
            rawQuery = path.substring(q + 1);
        }

        // normalize leading slash
        if (rawPath.startsWith("/")) {
            rawPath = rawPath.substring(1);
        }

        // ensure .json suffix on the path portion
        if (!rawPath.endsWith(".json")) {
            rawPath = rawPath + ".json";
        }

        HttpUrl.Builder b = base.newBuilder();
        // add each segment to avoid double slashes
        if (!rawPath.isEmpty()) {
            for (String seg : rawPath.split("/")) {
                if (!seg.isEmpty()) {
                    b.addPathSegment(seg);
                }
            }
        }

        // preserve existing base query and add provided query parameters, if any
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String kv : rawQuery.split("&")) {
                int eq = kv.indexOf('=');
                String k = eq >= 0 ? kv.substring(0, eq) : kv;
                String v = eq >= 0 ? kv.substring(eq + 1) : "";
                b.addEncodedQueryParameter(k, v);
            }
        }
        return b.build().toString();
    }
}
