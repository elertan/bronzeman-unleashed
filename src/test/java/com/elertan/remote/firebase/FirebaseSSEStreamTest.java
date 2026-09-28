package com.elertan.remote.firebase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.Test;

public class FirebaseSSEStreamTest {

    @Test
    public void unsuccessfulConnectionRetriesAfterConfiguredBackoff() throws Exception {
        try (FailingHttpServer server = new FailingHttpServer()) {
            FirebaseSSEStream stream = createStream(server.getPort());
            try {
                stream.start();

                assertTrue("first request was not received", server.awaitFirstRequest(1, TimeUnit.SECONDS));
                assertTrue("retry request was not received", server.awaitSecondRequest(4, TimeUnit.SECONDS));

                long retryDelayMillis = server.retryDelayMillis();
                assertTrue("retry occurred before the configured delay", retryDelayMillis >= 1_200L);
                assertTrue("retry occurred after the configured delay", retryDelayMillis < 3_000L);
            } finally {
                stream.stop();
            }
        }
    }

    @Test
    public void stoppingDuringBackoffPreventsRetry() throws Exception {
        try (FailingHttpServer server = new FailingHttpServer()) {
            FirebaseSSEStream stream = createStream(server.getPort());
            try {
                stream.start();

                assertTrue("first request was not received",
                    server.awaitFirstRequest(1, TimeUnit.SECONDS));
                // Give the stream time to handle the failure and schedule its retry
                assertFalse("stream retried without backing off",
                    server.awaitSecondRequest(500, TimeUnit.MILLISECONDS));
                stream.stop();

                assertFalse("stream retried after it was stopped",
                    server.awaitSecondRequest(3, TimeUnit.SECONDS));
            } finally {
                stream.stop();
            }
        }
    }

    private static FirebaseSSEStream createStream(int port) throws Exception {
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .dns(hostname -> Collections.singletonList(InetAddress.getLoopbackAddress()))
            .build();
        FirebaseRealtimeDatabaseURL databaseURL = new FirebaseRealtimeDatabaseURL(
            "http://test.firebaseio.com:" + port
        );
        return new FirebaseSSEStream(httpClient, new Gson(), databaseURL);
    }

    private static final class FailingHttpServer implements AutoCloseable {

        private final ServerSocket serverSocket = new ServerSocket(0);
        private final CountDownLatch firstRequest = new CountDownLatch(1);
        private final CountDownLatch secondRequest = new CountDownLatch(1);
        private final List<Long> requestTimes = new CopyOnWriteArrayList<>();
        private final Thread serverThread;

        private FailingHttpServer() throws IOException {
            serverThread = new Thread(this::serve, "firebase-sse-test-server");
            serverThread.setDaemon(true);
            serverThread.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private boolean awaitFirstRequest(long timeout, TimeUnit unit) throws InterruptedException {
            return firstRequest.await(timeout, unit);
        }

        private boolean awaitSecondRequest(long timeout, TimeUnit unit) throws InterruptedException {
            return secondRequest.await(timeout, unit);
        }

        private long retryDelayMillis() {
            return TimeUnit.NANOSECONDS.toMillis(requestTimes.get(1) - requestTimes.get(0));
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    try {
                        readRequest(socket);
                        recordRequest();
                        writeFailureResponse(socket);
                    } catch (IOException ignored) {
                        // The stream may cancel its active connection while the server responds.
                    }
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        private void recordRequest() {
            requestTimes.add(System.nanoTime());
            if (requestTimes.size() == 1) {
                firstRequest.countDown();
            } else if (requestTimes.size() == 2) {
                secondRequest.countDown();
            }
        }

        private static void readRequest(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(),
                StandardCharsets.UTF_8
            ));
            while (true) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    return;
                }
            }
        }

        private static void writeFailureResponse(Socket socket) throws IOException {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(),
                StandardCharsets.UTF_8
            ));
            writer.write("HTTP/1.1 500 Internal Server Error\r\n");
            writer.write("Content-Length: 0\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            serverThread.join(1_000L);
        }
    }
}
