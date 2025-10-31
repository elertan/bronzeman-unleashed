package com.elertan.remote.firebase;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
    private final OkHttpClient sseClient;
    private ExecutorService streamExecutor;
    private ExecutorService readExecutor;
    private volatile Call currentCall;

    @Getter
    private volatile boolean isRunning = false;

    // Watchdog: reconnect if no successful read for this long
    private static final long WATCHDOG_IDLE_NANOS = TimeUnit.MINUTES.toNanos(5);
    // Tracks last successful line read time for watchdog
    private volatile long lastReadNano = System.nanoTime();

    public FirebaseSSEStream(OkHttpClient httpClient, Gson gson,
        FirebaseRealtimeDatabaseURL databaseURL) {
        this.gson = gson;
        this.databaseURL = databaseURL;
        this.sseClient = httpClient.newBuilder()
            .retryOnConnectionFailure(true)
            // Keep the TCP/TLS connection alive and detect dead HTTP/2 sockets after sleep
            .pingInterval(Duration.ofSeconds(30))
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

    private synchronized ExecutorService ensureReadExecutor() {
        if (readExecutor == null || readExecutor.isShutdown()) {
            readExecutor = newSingleThreadExecutor("firebase-sse-read");
        }
        return readExecutor;
    }

    private synchronized void recreateReadExecutor() {
        if (readExecutor != null) {
            readExecutor.shutdownNow();
        }
        readExecutor = newSingleThreadExecutor("firebase-sse-read");
        log.warn("Firebase read executor recreated");
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
        if (isRunning) {
            return;
        }
        if (streamExecutor == null || streamExecutor.isShutdown()) {
            streamExecutor = newSingleThreadExecutor("firebase-sse-stream");
        }
        ensureReadExecutor();
        setIsRunning(true);
        streamExecutor.submit(this::loop);
    }

    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        setIsRunning(false);
        Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
        if (streamExecutor != null) {
            streamExecutor.shutdownNow();
            streamExecutor = null;
        }
        if (readExecutor != null) {
            readExecutor.shutdownNow();
            readExecutor = null;
        }

        log.info("Firebase SSE stream stopped");
    }

    private void setIsRunning(boolean running) {
        boolean changed = this.isRunning != running;
        this.isRunning = running;
        if (!changed) {
            return;
        }
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
                .header("Cache-Control", "no-cache")
                .build();

            try {
                Call call = sseClient.newCall(request);
                currentCall = call;
                try (Response response = call.execute()) {
                    if (!response.isSuccessful()) {
                        log.warn("Firebase stream HTTP {}. Will retry.", response.code());
                        if (!isRunning) {
                            break;
                        }
                        // Drop any potentially stale sockets after sleep/wake
                        sseClient.connectionPool().evictAll();
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
                        if (!isRunning) {
                            break;
                        }
                        sseClient.connectionPool().evictAll();
                        sleepWithJitterSeconds(backoffSeconds);
                        backoffSeconds = Math.min(backoffSeconds * 2, maxBackoffSeconds);
                        continue;
                    }

                    lastReadNano = System.nanoTime();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
                        // read loop; timeouts are treated as keep-alives
                        readStream(reader);
                    }

                    // successful session; reset backoff
                    backoffSeconds = 1;
                }
            } catch (Exception e) {
                if (!isRunning) {
                    break;
                }
                log.warn("Firebase stream error. Will retry.", e);
                // After sleep, TLS sockets in the pool may be invalid. Clear them.
                sseClient.connectionPool().evictAll();
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
        int consecutiveTimeouts = 0;

        while (isRunning) {
            try {
                String line = readLineWithTimeout(reader, READ_TIMEOUT_SECONDS);
                if (line == null) {
                    log.warn("Firebase stream closed by server");
                    break;
                }

                // successful read
                consecutiveTimeouts = 0;
                lastReadNano = System.nanoTime();

                if (line.startsWith(EVENT_PREFIX)) {
                    eventType = parseEventType(line);
                    if (eventType == null) {
                        break;
                    }
                    continue;
                }

                if (line.startsWith(DATA_PREFIX)) {
                    handleDataLine(line, eventType);
                }
            } catch (TimeoutException te) {
                consecutiveTimeouts++;
                // expected idle timeout; treat as liveness check
                log.debug("Firebase stream read timeout; continuing");

                // Watchdog: if no successful read for too long, force reconnect
                long idle = System.nanoTime() - lastReadNano;
                if (idle > WATCHDOG_IDLE_NANOS) {
                    log.warn(
                        "Firebase stream watchdog tripped after {} ms idle; reconnecting",
                        TimeUnit.NANOSECONDS.toMillis(idle)
                    );
                    Call call = currentCall;
                    if (call != null) {
                        call.cancel();
                    }
                    // Ensure we do not reuse a stale connection after system sleep
                    sseClient.connectionPool().evictAll();
                    break; // exit read loop to allow outer loop to reconnect
                }

                // Harden: if the read executor is misbehaving, recreate it
                if (consecutiveTimeouts >= 4) { // ~6 minutes with 90s timeouts
                    log.warn(
                        "Firebase read timeouts consecutive={} – recreating read executor",
                        consecutiveTimeouts
                    );
                    recreateReadExecutor();
                    consecutiveTimeouts = 0;
                }
                continue;
            }
        }
    }

    private String readLineWithTimeout(BufferedReader reader, int timeoutSeconds) throws Exception {
        ExecutorService exec = ensureReadExecutor();
        Future<String> futureLine;
        try {
            futureLine = exec.submit(reader::readLine);
        } catch (java.util.concurrent.RejectedExecutionException rex) {
            log.warn("Firebase read executor rejected task; recreating and retrying once");
            recreateReadExecutor();
            futureLine = readExecutor.submit(reader::readLine);
        }
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

    private class FirebaseSSEDataLine {

        private final String path;
        private final JsonElement data;

        public FirebaseSSEDataLine(String path, JsonElement data) {
            this.path = path;
            this.data = data;
        }
    }

}
