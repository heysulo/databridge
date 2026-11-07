package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.common.ChannelConnection;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.ProtocolHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

final class ServerInboundHandler extends ProtocolHandler {

    private static final String VERSION_MISMATCH_REASON = "Protocol version mismatch";

    private final Server server;
    private RemoteConnection connection;

    ServerInboundHandler(Server server) {
        super(server.settings(), HandshakeMessage.class);
        this.server = server;
    }

    @Override
    protected ChannelConnection connection() {
        return connection;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        connection = new RemoteConnection(ctx.channel());
        server.channelOpened(ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    protected void onHandshake(ChannelHandlerContext ctx, Message handshake) {
        if (((HandshakeMessage) handshake).getVersion() != HandshakeMessage.CURRENT_VERSION) {
            ctx.writeAndFlush(HandshakeAckMessage.rejected(VERSION_MISMATCH_REASON))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        establish(ctx);
        server.connectionEstablished(connection);
        ctx.writeAndFlush(HandshakeAckMessage.accepted());
        server.getCallback().onConnect(server, connection);
    }

    @Override
    protected void onMessage(Message message) {
        server.messageReceived();
        server.getCallback().onMessage(server, connection, message);
    }

    @Override
    protected void onError(Throwable cause) {
        server.getCallback().onError(server, connection, cause);
    }

    @Override
    protected void onDisconnected() {
        server.getCallback().onDisconnect(server, connection);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        server.channelClosed(connection);
        super.channelInactive(ctx);
    }
}
