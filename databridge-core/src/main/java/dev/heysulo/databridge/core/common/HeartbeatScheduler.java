package dev.heysulo.databridge.core.common;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

/**
 * Sends heartbeats on a fixed schedule. Heartbeats must not depend on writer idleness: replying to a peer's heartbeat
 * is itself a write, so an idle-based sender can be silenced indefinitely by a peer that heartbeats first, leaving
 * this side without a latency measurement.
 */
public final class HeartbeatScheduler {

    private HeartbeatScheduler() {
    }

    /**
     * Starts sending heartbeats, or returns {@code null} if {@code intervalSeconds} disables them.
     */
    public static ScheduledFuture<?> start(ChannelHandlerContext ctx, int intervalSeconds) {
        if (intervalSeconds <= 0) {
            return null;
        }
        return ctx.executor().scheduleAtFixedRate(
                () -> sendHeartbeat(ctx),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS);
    }

    private static void sendHeartbeat(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            ctx.writeAndFlush(new HeartbeatMessage(System.nanoTime(), false));
        }
    }
}
