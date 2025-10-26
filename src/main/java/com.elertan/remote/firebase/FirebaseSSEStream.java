package com.elertan.remote.firebase;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Slf4j
public class FirebaseSSEStream {
    private static final int READ_TIMEOUT_SECONDS = 90;

    private static final String EVENT_PREFIX = "event:";
    private static final int EVENT_PREFIX_LENGTH = EVENT_PREFIX.length();
    private static final String DATA_PREFIX = "data:";
    private static final int DATA_PREFIX_LENGTH = DATA_PREFIX.length();

    private final Gson gson;
    private final FirebaseRealtimeDatabaseURL databaseURL;

    private final CopyOnWriteArrayList<Consumer<FirebaseSSE>> serverSentEventListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> isRunningListeners = new CopyOnWriteArrayList<>();

    private ExecutorService streamExecutor;
    private ExecutorService readExecutor;

    private final OkHttpClient sseClient;
    private volatile Call currentCall;

    @Getter
    private volatile boolean isRunning = false;

    public FirebaseSSEStream(OkHttpClient httpClient, Gson gson, FirebaseRealtimeDatabaseURL databaseURL) {
        this.gson = gson;
        this.databaseURL = databaseURL;
        this.sseClient = httpClient.newBuilder()
                .retryOnConnectionFailure(true)
                .readTimeout(Duration.ZERO)
                .build();
    }

    private static ExecutorService newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thr, ex) -> log.error("{} uncaught", threadName, ex));
            return t;
        });
    }

    private void sleepWithJitterSeconds(int baseSeconds) {
        long jitterMillis = ThreadLocalRandom.current().nextLong(250, 1250);
        long totalMillis = baseSeconds * 1000L + jitterMillis;
        long deadline = System.currentTimeMillis() + totalMillis;
        while (isRunning && System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(250L, Math.max(1L, remaining)));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void addServerSentEventListener(Consumer<FirebaseSSE> listener) {
        serverSentEventListeners.add(listener);
    }

    public void removeServerSentEventListener(Consumer<FirebaseSSE> listener) {
        serverSentEventListeners.remove(listener);
    }

    public void addIsRunningListener(Runnable listener) {
        isRunningListeners.add(listener);
    }

    public void removeIsRunningListener(Runnable listener) {
        isRunningListeners.remove(listener);
    }

    public synchronized void start() {
        if (isRunning) return;
        if (streamExecutor == null || streamExecutor.isShutdown()) {
            streamExecutor = newSingleThreadExecutor("firebase-sse-stream");
        }
        if (readExecutor == null || readExecutor.isShutdown()) {
            readExecutor = newSingleThreadExecutor("firebase-sse-read");
        }
        setIsRunning(true);
        streamExecutor.submit(this::loop);
    }

    public synchronized void stop() {
        if (!isRunning) return;
        Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
        setIsRunning(false);
        if (streamExecutor != null) {
            streamExecutor.shutdownNow();
            streamExecutor = null;
        }
        if (readExecutor != null) {
            readExecutor.shutdownNow();
            readExecutor = null;
        }
    }

    private void setIsRunning(boolean running) {
        boolean changed = this.isRunning != running;
        this.isRunning = running;
        if (!changed) return;
        for (Runnable listener : isRunningListeners) {
            try {
                listener.run();
            } catch (Throwable t) {
                log.warn("isRunning listener error", t);
            }
        }
    }

    private void loop() {
        int backoffSeconds = 1;       // start small
        final int maxBackoffSeconds = 30;
        boolean loggedStart = false;

        while (isRunning) {
            final String url = databaseURL.getBaseUrl() + "/.json";

            Request request = FirebaseRealtimeDatabase.getRequestBuilder(url)
                    .header("Accept", "text/event-stream")
                    .header("Connection", "keep-alive")
                    .build();

            try {
                Call call = sseClient.newCall(request);
                currentCall = call;
                try (Response response = call.execute()) {
                    if (!response.isSuccessful()) {
                        log.warn("Firebase stream HTTP {}. Will retry.", response.code());
                        if (!isRunning) break;
                        sleepWithJitterSeconds(backoffSeconds);
                        backoffSeconds = Math.min(backoffSeconds * 2, maxBackoffSeconds);
                        continue;
                    }

                    if (!loggedStart) {
                        log.info("Firebase SSE stream connected");
                        loggedStart = true;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        log.warn("Firebase stream response body is null. Retrying.");
                        if (!isRunning) break;
                        sleepWithJitterSeconds(backoffSeconds);
                        backoffSeconds = Math.min(backoffSeconds * 2, maxBackoffSeconds);
                        continue;
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
                        // read loop; timeouts are treated as keep-alives
                        readStream(reader);
                    }

                    // successful session; reset backoff
                    backoffSeconds = 1;
                }
            } catch (Exception e) {
                if (!isRunning) break;
                log.warn("Firebase stream error. Will retry.", e);
                sleepWithJitterSeconds(backoffSeconds);
                backoffSeconds = Math.min(backoffSeconds * 2, maxBackoffSeconds);
            } finally {
                currentCall = null;
            }
        }
        setIsRunning(false);
    }

    private void readStream(BufferedReader reader) throws Exception {
        FirebaseSSEType eventType = null;

        while (isRunning) {
            try {
                String line = readLineWithTimeout(reader, READ_TIMEOUT_SECONDS);
                if (line == null) {
                    log.warn("Firebase stream closed by server");
                    break;
                }

                if (line.startsWith(EVENT_PREFIX)) {
                    eventType = parseEventType(line);
                    if (eventType == null) break;
                    continue;
                }

                if (line.startsWith(DATA_PREFIX)) {
                    handleDataLine(line, eventType);
                }
            } catch (TimeoutException te) {
                // expected idle timeout; treat as liveness check
                log.debug("Firebase stream read timeout; continuing");
                continue;
            }
        }
    }

    private String readLineWithTimeout(BufferedReader reader, int timeoutSeconds) throws Exception {
        Future<String> futureLine = readExecutor.submit(reader::readLine);
        try {
            return futureLine.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            futureLine.cancel(true);
            throw new TimeoutException("Firebase stream read timeout");
        }
    }

    private FirebaseSSEType parseEventType(String line) {
        String eventTypeString = line.substring(EVENT_PREFIX_LENGTH).trim();
        FirebaseSSEType type = FirebaseSSEType.fromRaw(eventTypeString);
        if (type == null) {
            log.error("Unknown Firebase event type: {}", eventTypeString);
        }
        return type;
    }

    private class FirebaseSSEDataLine {
        private final String path;
        private final JsonElement data;

        public FirebaseSSEDataLine(String path, JsonElement data) {
            this.path = path;
            this.data = data;
        }
    }

    private void handleDataLine(String line, FirebaseSSEType eventType) {
        if (eventType == null) {
            log.warn("Received data before event type");
            return;
        }

        switch (eventType) {
            case KeepAlive:
                log.debug("Firebase KeepAlive received");
                return;
            case Cancel:
                log.error("Firebase stream Cancel received");
                throw new IllegalStateException("Stream canceled by Firebase");
            case AuthRevoked:
                log.error("Firebase stream AuthRevoked received");
                throw new IllegalStateException("Stream auth revoked");
            default:
                break;
        }

        String jsonStr = line.substring(DATA_PREFIX_LENGTH).trim();
        if (jsonStr.isEmpty()) {
            log.warn("Firebase data line empty");
            return;
        }

        FirebaseSSEDataLine dataLine = gson.fromJson(jsonStr, FirebaseSSEDataLine.class);
        if (dataLine == null) {
            log.error("Failed to parse firebase data line");
            return;
        }

        FirebaseSSE eventData = new FirebaseSSE(eventType, dataLine.path, dataLine.data);
        for (Consumer<FirebaseSSE> listener : serverSentEventListeners) {
            try {
                listener.accept(eventData);
            } catch (Exception e) {
                log.warn("Firebase listener failed", e);
            }
        }
    }

}
