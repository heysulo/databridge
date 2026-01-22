package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.common.Message;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

public class ClientInboundHandler extends ChannelInboundHandlerAdapter {
    private final Client client;

    public ClientInboundHandler(Client client) {
        super();
        this.client = client;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
            ctx.writeAndFlush(new dev.heysulo.databridge.core.common.HeartbeatMessage(System.nanoTime(), false));
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof dev.heysulo.databridge.core.common.HeartbeatMessage) {
            dev.heysulo.databridge.core.common.HeartbeatMessage hb = (dev.heysulo.databridge.core.common.HeartbeatMessage) msg;
            if (hb.isReply) {
                long rtt = System.nanoTime() - hb.timestamp;
                client.updateLatency(rtt / 1_000_000); // Convert to ms
            } else {
                ctx.writeAndFlush(new dev.heysulo.databridge.core.common.HeartbeatMessage(hb.timestamp, true));
            }
            return;
        }

        client.getWorkerPool().execute(() -> {
            try {
                this.client.getCallback().OnMessage(client, (Message) msg);
            } catch (Exception e) {
                this.client.getCallback().OnError(client, e);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        client.getWorkerPool().execute(() -> this.client.getCallback().OnError(client, cause));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.client.channelHandlerContext = ctx;
        // Send Handshake
        ctx.writeAndFlush(
                new dev.heysulo.databridge.core.common.HandshakeMessage("Client-" + java.util.UUID.randomUUID()));

        client.getWorkerPool().execute(() -> this.client.getCallback().OnConnect(client));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        client.getWorkerPool().execute(() -> this.client.getCallback().OnDisconnect(client));
    }
}
