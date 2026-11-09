package dev.heysulo.databridge.core.common;

import java.security.cert.Certificate;
import java.util.List;
import java.util.OptionalLong;

/**
 * A live link to a remote peer.
 */
public interface Connection {

    /**
     * Queues a message for delivery. Failures after this call returns, such as a message that cannot be serialized,
     * are reported through the {@code onError} callback.
     */
    void send(Message message);

    boolean isConnected();

    /**
     * Closes the connection. Waits for completion unless called from a callback, where waiting is not possible.
     */
    void disconnect() throws InterruptedException;

    /**
     * Returns the latest round-trip time, or empty until a heartbeat has been answered.
     */
    OptionalLong getLatencyMillis();

    /**
     * Routes this connection's events to {@code callback} instead of the server's or client's callback, which lets
     * each connection have its own handler. Passing {@code null} restores the original callback.
     */
    void setCallback(ConnectionCallback callback);

    /**
     * Whether the connection is secured with TLS and its handshake has completed.
     */
    default boolean isSecure() {
        return false;
    }

    /**
     * Returns the certificates the peer presented during the TLS handshake, its own first, or an empty list if the
     * connection is plain, the handshake has not completed, or the peer presented none (as a client need not).
     */
    default List<Certificate> getPeerCertificates() {
        return List.of();
    }

    String getRemoteAddress();

    int getRemotePort();
}
