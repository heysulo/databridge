package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.RemoteClient;
import dev.heysulo.databridge.core.common.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ServerInboundHandler extends ChannelInboundHandlerAdapter {
    private final Server server;
    private Client remoteClient;
    private boolean handshaken = false;

    public ServerInboundHandler(Server server) {
        super();
        this.server = server;
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
                server.updateLatency(rtt / 1_000_000); // Convert to ms
            } else {
                ctx.writeAndFlush(new dev.heysulo.databridge.core.common.HeartbeatMessage(hb.timestamp, true));
            }
            return;
        }

        if (msg instanceof dev.heysulo.databridge.core.common.HandshakeMessage) {
            dev.heysulo.databridge.core.common.HandshakeMessage handshake = (dev.heysulo.databridge.core.common.HandshakeMessage) msg;
            if (handshake.version != dev.heysulo.databridge.core.common.HandshakeMessage.CURRENT_VERSION) {
                ctx.close(); // Bad Version
                return;
            }
            handshaken = true;
            return;
        }

        if (!handshaken) {
            ctx.close(); // Must handshake first
            return;
        }

        server.incrementMessages();
        server.getWorkerPool().execute(() -> {
            try {
                server.getCallback().OnMessage(server, remoteClient, (Message) msg);
            } catch (Exception e) {
                server.getCallback().OnError(server, remoteClient, e);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (remoteClient != null) {
            server.getWorkerPool().execute(() -> server.getCallback().OnError(server, remoteClient, cause));
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        server.incrementConnections();
        this.remoteClient = new RemoteClient(ctx);
        // Handshake: Wait for client to send HandshakeMessage
        server.getWorkerPool().execute(() -> server.getCallback().OnConnect(server, remoteClient));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        server.decrementConnections();
        if (remoteClient != null) {
            server.getWorkerPool().execute(() -> server.getCallback().OnDisconnect(server, remoteClient));
        }
    }
}
