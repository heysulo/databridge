package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import dev.heysulo.databridge.core.common.HeartbeatScheduler;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.MessageDecoder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.UUID;

public class ClientInboundHandler extends ChannelInboundHandlerAdapter {
    private final Client client;
    private boolean handshaken = false;
    private ScheduledFuture<?> heartbeatTask;

    public ClientInboundHandler(Client client) {
        super();
        this.client = client;
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HandshakeAckMessage) {
            handleHandshakeAck(ctx, (HandshakeAckMessage) msg);
            return;
        }

        if (!handshaken) {
            rejectProtocolViolation(ctx, "Received data before handshake completed");
            return;
        }

        if (msg instanceof HeartbeatMessage) {
            HeartbeatMessage hb = (HeartbeatMessage) msg;
            if (hb.isReply) {
                long rtt = System.nanoTime() - hb.timestamp;
                client.updateLatency(rtt / 1_000_000); // Convert to ms
            } else {
                ctx.writeAndFlush(new HeartbeatMessage(hb.timestamp, true));
            }
            return;
        }

        if (!(msg instanceof Message)) {
            rejectProtocolViolation(ctx, "Received a frame that is not a Message: " + msg.getClass().getName());
            return;
        }

        if (client.getCallback() != null) {
            this.client.getCallback().OnMessage(client, (Message) msg);
        }
    }

    private void handleHandshakeAck(ChannelHandlerContext ctx, HandshakeAckMessage ack) {
        if (handshaken) {
            rejectProtocolViolation(ctx, "Received a duplicate handshake acknowledgement");
            return;
        }
        if (!ack.success) {
            rejectProtocolViolation(ctx, "Handshake rejected: " + ack.message);
            return;
        }
        this.handshaken = true;
        MessageDecoder decoder = ctx.pipeline().get(MessageDecoder.class);
        if (decoder != null) {
            decoder.completeHandshake();
        }
        heartbeatTask = HeartbeatScheduler.start(ctx, client.getHeartbeatInterval());
        if (client.getCallback() != null) {
            client.getCallback().OnConnect(client);
        }
    }

    private void rejectProtocolViolation(ChannelHandlerContext ctx, String reason) {
        ctx.close();
        if (client.getCallback() != null) {
            client.getCallback().OnError(client, new IllegalStateException(reason));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof DecoderException) {
            ctx.close(); // The inbound stream can no longer be trusted
        }
        if (client.getCallback() != null) {
            this.client.getCallback().OnError(client, cause);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.client.channelHandlerContext = ctx;
        // Send Handshake
        ctx.writeAndFlush(
                new HandshakeMessage("Client-" + UUID.randomUUID()));
        // Note: OnConnect will be triggered once HandshakeAckMessage is received
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelHeartbeats();
        if (handshaken && client.getCallback() != null) {
            this.client.getCallback().OnDisconnect(client);
        }
    }

    private void cancelHeartbeats() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }
}
