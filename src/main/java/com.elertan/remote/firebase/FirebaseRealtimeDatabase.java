package com.elertan.remote.firebase;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final Gson gson;
    @Getter private final FirebaseRealtimeDatabaseURL databaseURL;
    @Getter private final FirebaseSSEStream stream;

    public FirebaseRealtimeDatabase(OkHttpClient httpClient, Gson gson, FirebaseRealtimeDatabaseURL databaseURL) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.databaseURL = databaseURL;
        this.stream = new FirebaseSSEStream(httpClient, gson, databaseURL);
    }

    public static CompletableFuture<Boolean> canConnectTo(OkHttpClient httpClient, FirebaseRealtimeDatabaseURL url) {
        Request request = getRequestBuilder(url.getBaseUrl() + "/__BRONZEMAN_UNLEASED_CAN_CONNECT_TEST.json").get().build();
        return enqueueAsync(httpClient, request).handle((response, error) -> {
            if (error != null) { log.error("FirebaseRealtimeDatabase canConnect() exception: ", error); return Boolean.FALSE; }
            try (okhttp3.Response res = response) { return res.isSuccessful(); }
        });
    }

    public static Request.Builder getRequestBuilder(String url) {
        return new Request.Builder().url(url).header("User-Agent", "BronzemanUnleashedPlugin");
    }

    public static void validateBasePath(String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        if (!path.startsWith("/")) throw new IllegalArgumentException("path must start with '/'");
        if (path.lastIndexOf("/") != 0) throw new IllegalArgumentException("path must only be a starting resource");
    }

    private static CompletableFuture<okhttp3.Response> enqueueAsync(OkHttpClient client, Request request) {
        okhttp3.Call call = client.newCall(request);
        CompletableFuture<okhttp3.Response> future = new CompletableFuture<>();
        future.whenComplete((r, t) -> { if (future.isCancelled()) call.cancel(); });
        call.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call c, IOException e) { if (!future.isDone()) future.completeExceptionally(e); }
            @Override public void onResponse(Call c, okhttp3.Response response) {
                if (!future.isDone()) future.complete(response); else response.close();
            }
        });
        return future;
    }

    @Override
    public void close() throws Exception { stream.stop(); }

    public CompletableFuture<JsonElement> get(String path) {
        return executeRequest(new Request.Builder().url(getUrlForPath(path))
            .header("User-Agent", "BronzemanUnleashedPlugin").get().build(), this::parseJson);
    }

    public CompletableFuture<JsonElement> post(String path, JsonElement data) {
        return executeRequest(buildJsonRequest(path, "POST", data), this::parseJson);
    }

    public CompletableFuture<JsonElement> put(String path, JsonElement data) {
        return executeRequest(buildJsonRequest(path, "PUT", data), this::parseJson);
    }

    public CompletableFuture<Void> delete(String path) {
        return executeRequest(new Request.Builder().url(getUrlForPath(path))
            .header("User-Agent", "BronzemanUnleashedPlugin").delete().build(), body -> null);
    }

    private <T> CompletableFuture<T> executeRequest(Request request, Function<ResponseBody, T> bodyParser) {
        CompletableFuture<T> future = new CompletableFuture<>();
        okhttp3.Call call = httpClient.newCall(request);
        future.whenComplete((r, t) -> { if (future.isCancelled()) call.cancel(); });
        call.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call c, IOException e) {
                if (!future.isDone()) { log.error("{} {} failed", request.method(), request.url()); future.completeExceptionally(e); }
            }
            @Override public void onResponse(Call c, okhttp3.Response response) {
                try (okhttp3.Response res = response) {
                    if (!res.isSuccessful()) {
                        String snippet = null;
                        ResponseBody errBody = res.body();
                        if (errBody != null) { try { snippet = errBody.string(); } catch (IOException ignore) {} }
                        String msg = String.format("%s %s -> HTTP %d %s%s", request.method(), request.url(),
                            res.code(), res.message(), snippet != null ? ": " + snippet : "");
                        if (!future.isDone()) { log.error(msg); future.completeExceptionally(new IOException(msg)); }
                        return;
                    }
                    ResponseBody body = res.body();
                    if (body == null) {
                        if (!future.isDone()) future.complete(null);
                        return;
                    }
                    try { if (!future.isDone()) future.complete(bodyParser.apply(body)); }
                    catch (Exception e) { if (!future.isDone()) future.completeExceptionally(e); }
                }
            }
        });
        return future;
    }

    private JsonElement parseJson(ResponseBody body) {
        try (Reader reader = body.charStream()) { return gson.fromJson(reader, JsonElement.class); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    private Request buildJsonRequest(String path, String method, JsonElement data) {
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, gson.toJson(data));
        return getRequestBuilder(getUrlForPath(path)).header("Content-Type", "application/json").method(method, body).build();
    }

    private String getUrlForPath(String path) {
        HttpUrl base = HttpUrl.parse(databaseURL.getBaseUrl());
        if (base == null) throw new IllegalArgumentException("Invalid base URL: " + databaseURL.getBaseUrl());
        String rawPath = path, rawQuery = null;
        int q = path.indexOf('?');
        if (q >= 0) { rawPath = path.substring(0, q); rawQuery = path.substring(q + 1); }
        if (rawPath.startsWith("/")) rawPath = rawPath.substring(1);
        if (!rawPath.endsWith(".json")) rawPath = rawPath + ".json";
        HttpUrl.Builder b = base.newBuilder();
        if (!rawPath.isEmpty()) { for (String seg : rawPath.split("/")) { if (!seg.isEmpty()) b.addPathSegment(seg); } }
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String kv : rawQuery.split("&")) {
                int eq = kv.indexOf('=');
                b.addEncodedQueryParameter(eq >= 0 ? kv.substring(0, eq) : kv, eq >= 0 ? kv.substring(eq + 1) : "");
            }
        }
        return b.build().toString();
    }
}
