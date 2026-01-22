package dev.heysulo.databridge.core.common;

/**
 * Sent periodically to keep the connection alive and measure latency (RTT).
 */
public class HeartbeatMessage implements Message {
    private static final long serialVersionUID = 2L;

    public final long timestamp;
    public final boolean isReply;

    public HeartbeatMessage() {
        this(System.nanoTime(), false);
    }

    public HeartbeatMessage(long timestamp, boolean isReply) {
        this.timestamp = timestamp;
        this.isReply = isReply;
    }
}
