package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.RemoteClient;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import dev.heysulo.databridge.core.common.HeartbeatScheduler;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.MessageDecoder;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

public class ServerInboundHandler extends ChannelInboundHandlerAdapter {
    private final Server server;
    private Client remoteClient;
    private boolean handshaken = false;
    private ScheduledFuture<?> handshakeTimeoutTask;
    private ScheduledFuture<?> heartbeatTask;

    public ServerInboundHandler(Server server) {
        super();
        this.server = server;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            if (idleEvent.state() == IdleState.READER_IDLE) {
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HandshakeMessage) {
            handleHandshake(ctx, (HandshakeMessage) msg);
            return;
        }

        if (!handshaken) {
            ctx.close(); // Must handshake first
            return;
        }

        if (msg instanceof HeartbeatMessage) {
            HeartbeatMessage hb = (HeartbeatMessage) msg;
            if (hb.isReply) {
                long rtt = System.nanoTime() - hb.timestamp;
                long latency = rtt / 1_000_000; // Convert to ms
                remoteClient.updateLatency(latency);
                server.updateLatency(latency);
            } else {
                ctx.writeAndFlush(new HeartbeatMessage(hb.timestamp, true));
            }
            return;
        }

        if (!(msg instanceof Message)) {
            rejectProtocolViolation(ctx, "Received a frame that is not a Message: " + msg.getClass().getName());
            return;
        }

        server.incrementMessages();
        if (canUseRemoteClientCallback(remoteClient)) {
            remoteClient.getCallback().OnMessage(remoteClient, (Message) msg);
        } else {
            server.getCallback().OnMessage(server, remoteClient, (Message) msg);
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, HandshakeMessage handshake) {
        if (handshaken) {
            rejectProtocolViolation(ctx, "Received a duplicate handshake");
            return;
        }
        if (handshake.version != HandshakeMessage.CURRENT_VERSION) {
            ctx.writeAndFlush(new HandshakeAckMessage(
                    HandshakeMessage.CURRENT_VERSION,
                    false,
                    "Protocol version mismatch"
            )).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        handshaken = true;
        cancelHandshakeTimeout();
        MessageDecoder decoder = ctx.pipeline().get(MessageDecoder.class);
        if (decoder != null) {
            decoder.completeHandshake();
        }
        server.addConnectedClient(remoteClient);
        ctx.writeAndFlush(new HandshakeAckMessage(
                HandshakeMessage.CURRENT_VERSION,
                true,
                "OK"
        ));
        heartbeatTask = HeartbeatScheduler.start(ctx, server.getHeartbeatInterval());

        if (canUseRemoteClientCallback(remoteClient)) {
            remoteClient.getCallback().OnConnect(remoteClient);
        } else {
            server.getCallback().OnConnect(server, remoteClient);
        }
    }

    private void rejectProtocolViolation(ChannelHandlerContext ctx, String reason) {
        ctx.close();
        reportError(new IllegalStateException(reason));
    }

    private void reportError(Throwable cause) {
        if (remoteClient == null) {
            return;
        }
        if (canUseRemoteClientCallback(remoteClient)) {
            remoteClient.getCallback().OnError(remoteClient, cause);
        } else {
            server.getCallback().OnError(server, remoteClient, cause);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof DecoderException) {
            ctx.close(); // The inbound stream can no longer be trusted
        }
        reportError(cause);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        server.incrementConnections();
        server.trackChannel(ctx.channel());
        this.remoteClient = new RemoteClient(ctx);
        // Handshake: Wait for client to send HandshakeMessage before calling OnConnect
        this.handshakeTimeoutTask = ctx.executor().schedule(
                () -> {
                    ctx.close();
                },
                server.getHandshakeTimeout(),
                TimeUnit.SECONDS);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelHandshakeTimeout();
        cancelHeartbeats();
        server.decrementConnections();
        if (remoteClient != null) {
            server.removeConnectedClient(remoteClient);
        }
        if (handshaken && remoteClient != null) {
            if (canUseRemoteClientCallback(remoteClient)) {
                remoteClient.getCallback().OnDisconnect(remoteClient);
            } else {
                server.getCallback().OnDisconnect(server, remoteClient);
            }
        }
    }

    private void cancelHeartbeats() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void cancelHandshakeTimeout() {
        if (handshakeTimeoutTask != null) {
            handshakeTimeoutTask.cancel(false);
            handshakeTimeoutTask = null;
        }
    }

    private boolean canUseRemoteClientCallback(Client remoteClient) {
        return remoteClient != null && remoteClient.getCallback() != null;
    }
}
