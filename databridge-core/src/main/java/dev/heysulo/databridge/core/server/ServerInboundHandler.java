package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.RemoteClient;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import dev.heysulo.databridge.core.common.Message;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

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
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HeartbeatMessage) {
            HeartbeatMessage hb = (HeartbeatMessage) msg;
            if (hb.isReply) {
                long rtt = System.nanoTime() - hb.timestamp;
                server.updateLatency(rtt / 1_000_000); // Convert to ms
            } else {
                ctx.writeAndFlush(new HeartbeatMessage(hb.timestamp, true));
            }
            return;
        }

        if (msg instanceof HandshakeMessage) {
            HandshakeMessage handshake = (HandshakeMessage) msg;
            if (handshake.version != HandshakeMessage.CURRENT_VERSION) {
                ctx.writeAndFlush(new HandshakeAckMessage(
                        HandshakeMessage.CURRENT_VERSION,
                        false,
                        "Protocol version mismatch"
                )).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            handshaken = true;
            ctx.writeAndFlush(new HandshakeAckMessage(
                    HandshakeMessage.CURRENT_VERSION,
                    true,
                    "OK"
            ));

            if (canUseRemoteClientCallback(remoteClient)) {
                remoteClient.getCallback().OnConnect(remoteClient);
            } else {
                server.getCallback().OnConnect(server, remoteClient);
            }
            return;
        }

        if (!handshaken) {
            ctx.close(); // Must handshake first
            return;
        }

        server.incrementMessages();
        if (canUseRemoteClientCallback(remoteClient)) {
            remoteClient.getCallback().OnMessage(remoteClient, (Message) msg);
        } else {
            server.getCallback().OnMessage(server, remoteClient, (Message) msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (remoteClient != null) {
            if (canUseRemoteClientCallback(remoteClient)) {
                remoteClient.getCallback().OnError(remoteClient, cause);
            } else {
                server.getCallback().OnError(server, remoteClient, cause);
            }
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        server.incrementConnections();
        this.remoteClient = new RemoteClient(ctx);
        // Handshake: Wait for client to send HandshakeMessage before calling OnConnect
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        server.decrementConnections();
        if (handshaken && remoteClient != null) {
            if (canUseRemoteClientCallback(remoteClient)) {
                remoteClient.getCallback().OnDisconnect(remoteClient);
            } else {
                server.getCallback().OnDisconnect(server, remoteClient);
            }
        }
    }

    private boolean canUseRemoteClientCallback(Client remoteClient) {
        return remoteClient != null && remoteClient.getCallback() != null;
    }
}
