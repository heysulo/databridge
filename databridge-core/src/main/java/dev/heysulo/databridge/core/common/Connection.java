package dev.heysulo.databridge.core.common;

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

    String getRemoteAddress();

    int getRemotePort();
}
