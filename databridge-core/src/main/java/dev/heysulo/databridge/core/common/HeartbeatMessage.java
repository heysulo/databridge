package dev.heysulo.databridge.core.common;

/**
 * Sent periodically to keep the connection alive and measure latency (RTT).
 */
public final class HeartbeatMessage implements Message {
    private static final long serialVersionUID = 2L;

    private final long timestamp;
    private final boolean isReply;

    private HeartbeatMessage(long timestamp, boolean isReply) {
        this.timestamp = timestamp;
        this.isReply = isReply;
    }

    public static HeartbeatMessage request() {
        return new HeartbeatMessage(System.nanoTime(), false);
    }

    /**
     * Echoes the request's timestamp, which only the side that created it can interpret.
     */
    public static HeartbeatMessage replyTo(HeartbeatMessage request) {
        return new HeartbeatMessage(request.timestamp, true);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isReply() {
        return isReply;
    }
}
