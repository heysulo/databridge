package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.common.Message;

public class PongMessage implements Message {
    public final long originalTimestamp;
    public final int randomValue;

    public PongMessage(long originalTimestamp, int randomValue) {
        this.originalTimestamp = originalTimestamp;
        this.randomValue = randomValue;
    }
}
