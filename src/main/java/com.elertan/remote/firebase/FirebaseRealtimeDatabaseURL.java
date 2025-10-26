package com.elertan.remote.firebase;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Value object for Firebase Realtime Database endpoints.
 * <p>
 * Ensures the host belongs to an allowed Firebase domain and provides a
 * normalized base URL in the form {@code scheme://host[:port]}.
 * Handles IPv6 literals and omits default ports (80 for http, 443 for https).
 */
public final class FirebaseRealtimeDatabaseURL {
    private static final List<String> ALLOWED_SUFFIXES = Arrays.asList(
            ".firebasedatabase.app",
            ".firebaseio.com"
    );

    private final URL url;

    /**
     * Construct from a {@link URL}.
     *
     * @param url non-null URL using http or https and pointing to an allowed Firebase host
     * @throws IllegalArgumentException if the URL is null, uses an unsupported scheme, or the host is not a Firebase host
     */
    public FirebaseRealtimeDatabaseURL(URL url) throws IllegalArgumentException {
        this.url = Objects.requireNonNull(url, "url");

        final String protocol = this.url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("Protocol must be http or https");
        }

        if (!isFirebaseHost(this.url.getHost())) {
            throw new IllegalArgumentException("The provided URL is not a valid FirebaseRealtimeDatabaseURL");
        }
    }

    /**
     * Construct from a string.
     *
     * @param urlString textual URL
     * @throws IllegalArgumentException if the string is not a valid URL or not a Firebase host
     */
    public FirebaseRealtimeDatabaseURL(String urlString) throws IllegalArgumentException, MalformedURLException {
        this(new URL(urlString));
    }

    /**
     * Returns the normalized base URL: {@code scheme://host[:port]}. Omits default ports.
     */
    public String getBaseUrl() {
        final String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        final String host = url.getHost();
        final int port = url.getPort();

        final boolean isIpv6 = host != null && host.indexOf(':') >= 0; // literal IPv6 host

        StringBuilder base = new StringBuilder();
        base.append(protocol).append("://");
        if (isIpv6) {
            base.append('[').append(host).append(']');
        } else {
            base.append(host);
        }

        if (port != -1 && !isDefaultPort(protocol, port)) {
            base.append(":").append(port);
        }

        return base.toString();
    }

    /**
     * Exposes the underlying URL instance.
     */
    public URL getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return url.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FirebaseRealtimeDatabaseURL that = (FirebaseRealtimeDatabaseURL) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    private static boolean isDefaultPort(String protocol, int port) {
        return ("http".equalsIgnoreCase(protocol) && port == 80)
                || ("https".equalsIgnoreCase(protocol) && port == 443);
    }

    private static boolean isFirebaseHost(String host) {
        if (host == null || host.isEmpty()) return false;
        final String h = host.toLowerCase(Locale.ROOT);
        for (String suffix : ALLOWED_SUFFIXES) {
            if (h.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
