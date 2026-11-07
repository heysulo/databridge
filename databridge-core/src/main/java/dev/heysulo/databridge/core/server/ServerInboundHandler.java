package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.common.ChannelConnection;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.ProtocolHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

final class ServerInboundHandler extends ProtocolHandler {

    private static final String VERSION_MISMATCH_REASON = "Protocol version mismatch";

    private final Server server;
    private RemoteConnection connection;
    private ScheduledFuture<?> handshakeTimeoutTask;

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
        handshakeTimeoutTask = ctx.executor().schedule(
                (Runnable) ctx::close, server.getHandshakeTimeout().toMillis(), TimeUnit.MILLISECONDS);
        super.channelActive(ctx);
    }

    @Override
    protected void onHandshake(ChannelHandlerContext ctx, Message handshake) {
        if (((HandshakeMessage) handshake).getVersion() != HandshakeMessage.CURRENT_VERSION) {
            ctx.writeAndFlush(HandshakeAckMessage.rejected(VERSION_MISMATCH_REASON))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        handshakeTimeoutTask.cancel(false);
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
        handshakeTimeoutTask.cancel(false);
        server.channelClosed(connection);
        super.channelInactive(ctx);
    }
}
