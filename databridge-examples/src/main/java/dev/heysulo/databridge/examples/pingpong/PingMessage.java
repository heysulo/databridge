package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.common.Message;

public class PingMessage implements Message {
    public final long timestamp;

    public PingMessage() {
        this.timestamp = System.currentTimeMillis();
    }
}
