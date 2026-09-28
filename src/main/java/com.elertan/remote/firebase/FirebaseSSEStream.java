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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public class FirebaseSSEStream {

    private static final int READ_TIMEOUT_SECONDS = 90;
    private static final int MAX_BACKOFF_SECONDS = 30;

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
    private ScheduledExecutorService retryScheduler;
    private ScheduledFuture<?> scheduledRetry;
    private volatile Call currentCall;
    private int backoffSeconds;
    private boolean loggedStart;

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
            .connectionPool(new ConnectionPool())
            .retryOnConnectionFailure(true)
            // Keep the TCP/TLS connection alive and detect dead HTTP/2 sockets after sleep
            .pingInterval(Duration.ofSeconds(30))
            .readTimeout(Duration.ZERO)
            .build();
    }

    private static ExecutorService newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(r -> newDaemonThread(r, threadName));
    }

    private static ScheduledExecutorService newSingleThreadScheduledExecutor(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(r -> newDaemonThread(r, threadName));
    }

    private static Thread newDaemonThread(Runnable runnable, String threadName) {
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((thr, ex) -> log.error("{} uncaught", threadName, ex));
        return thread;
    }

    private synchronized ExecutorService ensureReadExecutor() {
        if (readExecutor == null || readExecutor.isShutdown()) {
            readExecutor = newSingleThreadExecutor("firebase-sse-read");
        }
        return readExecutor;
    }

    private synchronized void recreateReadExecutor() {
        if (readExecutor != null) {
            readExecutor.shutdown();
        }
        readExecutor = newSingleThreadExecutor("firebase-sse-read");
        log.warn("Firebase read executor recreated");
    }

    private synchronized void scheduleRetry() {
        if (!isRunning || retryScheduler == null || retryScheduler.isShutdown()) {
            return;
        }

        long jitterMillis = ThreadLocalRandom.current().nextLong(250, 1250);
        long delayMillis = backoffSeconds * 1000L + jitterMillis;
        backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_SECONDS);
        scheduledRetry = retryScheduler.schedule(this::submitConnectionAttempt,
            delayMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void submitConnectionAttempt() {
        scheduledRetry = null;
        if (!isRunning || streamExecutor == null || streamExecutor.isShutdown()) {
            return;
        }
        streamExecutor.submit(this::runConnectionAttempt);
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
        if (retryScheduler == null || retryScheduler.isShutdown()) {
            retryScheduler = newSingleThreadScheduledExecutor("firebase-sse-retry");
        }
        ensureReadExecutor();
        backoffSeconds = 1;
        loggedStart = false;
        setIsRunning(true);
        submitConnectionAttempt();
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
        if (scheduledRetry != null) {
            scheduledRetry.cancel(false);
            scheduledRetry = null;
        }
        if (retryScheduler != null) {
            retryScheduler.shutdown();
            retryScheduler = null;
        }
        if (streamExecutor != null) {
            streamExecutor.shutdown();
            streamExecutor = null;
        }
        if (readExecutor != null) {
            readExecutor.shutdown();
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

    private void runConnectionAttempt() {
        if (!isRunning) {
            return;
        }

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
                        return;
                    }
                    // Drop any potentially stale sockets after sleep/wake
                    sseClient.connectionPool().evictAll();
                    scheduleRetry();
                    return;
                }

                if (!loggedStart) {
                    log.debug("Firebase SSE stream connected");
                    loggedStart = true;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    log.warn("Firebase stream response body is null. Retrying.");
                    if (!isRunning) {
                        return;
                    }
                    sseClient.connectionPool().evictAll();
                    scheduleRetry();
                    return;
                }

                lastReadNano = System.nanoTime();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
                    // read loop; timeouts are treated as keep-alives
                    readStream(reader);
                }

                // successful session; reset backoff
                backoffSeconds = 1;
                submitConnectionAttempt();
            }
        } catch (Exception e) {
            if (!isRunning) {
                return;
            }
            log.warn("Firebase stream error. Will retry.", e);
            // After sleep, TLS sockets in the pool may be invalid. Clear them.
            sseClient.connectionPool().evictAll();
            scheduleRetry();
        } finally {
            currentCall = null;
        }
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
            futureLine.cancel(false);
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
