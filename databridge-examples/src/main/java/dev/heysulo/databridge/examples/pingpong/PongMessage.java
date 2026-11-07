package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.common.Message;

public class PongMessage implements Message {
    private final long originalTimestamp;
    private final int randomValue;

    public PongMessage(long originalTimestamp, int randomValue) {
        this.originalTimestamp = originalTimestamp;
        this.randomValue = randomValue;
    }

    public long getOriginalTimestamp() {
        return originalTimestamp;
    }

    public int getRandomValue() {
        return randomValue;
    }
}
