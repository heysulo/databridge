package dev.heysulo.databridge.core.common;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Protocol rules common to both ends of a connection: nothing but the handshake is accepted until it completes, and
 * afterwards each side sends its own heartbeats.
 *
 * <p>Heartbeats run on a fixed schedule rather than on writer idleness. Replying to a peer's heartbeat is itself a
 * write, so an idle-based sender can be silenced indefinitely by a peer that heartbeats first.
 */
public abstract class ProtocolHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolHandler.class);
    private static final long NANOS_PER_MILLI = TimeUnit.MILLISECONDS.toNanos(1);

    private final ProtocolSettings settings;
    private final Class<? extends Message> handshakeType;
    private boolean established = false;
    private ScheduledFuture<?> handshakeTimeoutTask;
    private ScheduledFuture<?> heartbeatTask;

    protected ProtocolHandler(ProtocolSettings settings, Class<? extends Message> handshakeType) {
        this.settings = settings;
        this.handshakeType = handshakeType;
    }

    protected abstract ChannelConnection connection();

    protected abstract void onHandshake(ChannelHandlerContext ctx, Message handshake);

    protected abstract void onMessage(Message message);

    protected abstract void onError(Throwable cause);

    protected abstract void onDisconnected();

    /**
     * Marks the handshake as complete, opening the connection to application messages.
     */
    protected final void establish(ChannelHandlerContext ctx) {
        established = true;
        handshakeTimeoutTask.cancel(false);
        LOGGER.debug("Connection to {} established", describePeer());
        MessageDecoder decoder = ctx.pipeline().get(MessageDecoder.class);
        if (decoder != null) {
            decoder.completeHandshake();
        }
        startHeartbeats(ctx);
    }

    protected final void rejectProtocolViolation(ChannelHandlerContext ctx, String reason) {
        logRejection("Closing connection to {}: {}", describePeer(), reason);
        ctx.close();
        onError(new IllegalStateException(reason));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        connection().attach(ctx);
        handshakeTimeoutTask = ctx.executor().schedule(
                () -> rejectProtocolViolation(ctx, "Handshake timed out"),
                settings.getHandshakeTimeout().toMillis(),
                TimeUnit.MILLISECONDS);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object frame) {
        if (handshakeType.isInstance(frame)) {
            readHandshake(ctx, handshakeType.cast(frame));
        } else if (!established) {
            rejectProtocolViolation(ctx, "Received data before the handshake completed");
        } else if (frame instanceof HeartbeatMessage) {
            readHeartbeat(ctx, (HeartbeatMessage) frame);
        } else if (frame instanceof Message) {
            onMessage((Message) frame);
        } else {
            rejectProtocolViolation(ctx, "Received a frame that is not a Message: " + frame.getClass().getName());
        }
    }

    private void readHandshake(ChannelHandlerContext ctx, Message handshake) {
        if (established) {
            rejectProtocolViolation(ctx, "Received a duplicate handshake");
        } else {
            onHandshake(ctx, handshake);
        }
    }

    private void readHeartbeat(ChannelHandlerContext ctx, HeartbeatMessage heartbeat) {
        if (heartbeat.isReply()) {
            long roundTripNanos = System.nanoTime() - heartbeat.getTimestamp();
            connection().recordLatency(roundTripNanos / NANOS_PER_MILLI);
        } else {
            ctx.writeAndFlush(HeartbeatMessage.replyTo(heartbeat));
        }
    }

    private void startHeartbeats(ChannelHandlerContext ctx) {
        Duration interval = settings.getHeartbeatInterval();
        if (interval.isZero()) {
            return;
        }
        long intervalMillis = interval.toMillis();
        heartbeatTask = ctx.executor().scheduleAtFixedRate(
                () -> sendHeartbeat(ctx), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private static void sendHeartbeat(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            ctx.writeAndFlush(HeartbeatMessage.request());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            if (((IdleStateEvent) event).state() == IdleState.READER_IDLE) {
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof DecoderException) {
            logRejection("Closing connection to {} after a decoding failure: {}", describePeer(), cause.toString());
            ctx.close(); // The inbound stream can no longer be trusted
        }
        onError(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        handshakeTimeoutTask.cancel(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (established) {
            LOGGER.debug("Connection to {} closed", describePeer());
            onDisconnected();
        }
        super.channelInactive(ctx);
    }

    // Peers that never authenticated stay at debug, so scanners cannot flood the logs
    private void logRejection(String format, String peer, String detail) {
        if (established) {
            LOGGER.warn(format, peer, detail);
        } else {
            LOGGER.debug(format, peer, detail);
        }
    }

    private String describePeer() {
        return connection().getRemoteAddress() + ":" + connection().getRemotePort();
    }
}
