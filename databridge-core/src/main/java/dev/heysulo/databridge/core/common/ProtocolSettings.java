package dev.heysulo.databridge.core.common;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Settings shared by both ends of a connection. Changes apply to connections opened afterwards.
 */
public final class ProtocolSettings {

    public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    public static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);
    // Netty's own default, so clients that never set it behave as before
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final List<String> trustedPackages = new CopyOnWriteArrayList<>(MessageDecoder.DEFAULT_TRUSTED_PACKAGES);
    private volatile int maxFrameLength = MessageDecoder.DEFAULT_MAX_FRAME_LENGTH;
    private volatile Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    private volatile Duration handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;
    private volatile Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    public void addTrustedPackage(String packagePattern) {
        trustedPackages.add(Objects.requireNonNull(packagePattern, "packagePattern"));
    }

    /**
     * Removes every rule, including the defaults, so that only rules added afterwards apply.
     */
    public void clearTrustedPackages() {
        trustedPackages.clear();
    }

    public List<String> getTrustedPackages() {
        return List.copyOf(trustedPackages);
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public void setMaxFrameLength(int maxFrameLength) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("Max frame length must be positive");
        }
        this.maxFrameLength = maxFrameLength;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Sets how often heartbeats are sent. A peer silent for two intervals is disconnected. {@link Duration#ZERO}
     * disables both.
     */
    public void setHeartbeatInterval(Duration heartbeatInterval) {
        Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        if (heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("Heartbeat interval must not be negative");
        }
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * Sets how long a new connection may take to complete the handshake before it is closed.
     */
    public void setHandshakeTimeout(Duration handshakeTimeout) {
        Objects.requireNonNull(handshakeTimeout, "handshakeTimeout");
        if (handshakeTimeout.isZero() || handshakeTimeout.isNegative()) {
            throw new IllegalArgumentException("Handshake timeout must be positive");
        }
        this.handshakeTimeout = handshakeTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets how long a client waits for the transport to connect. A host that has gone away often drops connection
     * attempts silently, so without a short timeout a client can wait this long before trying elsewhere. Ignored by
     * servers.
     */
    public void setConnectTimeout(Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        if (connectTimeout.isZero() || connectTimeout.isNegative() || connectTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Connect timeout must be positive and at most Integer.MAX_VALUE ms");
        }
        this.connectTimeout = connectTimeout;
    }
}
