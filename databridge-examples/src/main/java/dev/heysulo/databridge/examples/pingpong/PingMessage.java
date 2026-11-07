package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.common.Message;

public class PingMessage implements Message {
    private final long timestamp = System.currentTimeMillis();

    public long getTimestamp() {
        return timestamp;
    }
}
