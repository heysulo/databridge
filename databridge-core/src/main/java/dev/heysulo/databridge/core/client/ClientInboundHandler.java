package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import dev.heysulo.databridge.core.common.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.UUID;

public class ClientInboundHandler extends ChannelInboundHandlerAdapter {
    private final Client client;
    private boolean handshaken = false;

    public ClientInboundHandler(Client client) {
        super();
        this.client = client;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            if (idleEvent.state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(new HeartbeatMessage(System.nanoTime(), false));
            } else if (idleEvent.state() == IdleState.READER_IDLE) {
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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

        if (msg instanceof HandshakeAckMessage) {
            HandshakeAckMessage ack = (HandshakeAckMessage) msg;
            if (ack.success) {
                this.handshaken = true;
                if (client.getCallback() != null) {
                    client.getCallback().OnConnect(client);
                }
            } else {
                ctx.close();
                if (client.getCallback() != null) {
                    client.getCallback().OnError(client, new IllegalStateException("Handshake rejected: " + ack.message));
                }
            }
            return;
        }

        if (client.getCallback() != null) {
            this.client.getCallback().OnMessage(client, (Message) msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
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
        if (handshaken && client.getCallback() != null) {
            this.client.getCallback().OnDisconnect(client);
        }
    }
}
