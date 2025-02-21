package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.RemoteClient;
import dev.heysulo.databridge.core.common.Message;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ServerInboundHandler extends ChannelInboundHandlerAdapter {
    private final Server server;
    private Client remoteClient;

    public ServerInboundHandler(Server server) {
        super();
        this.server = server;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        System.out.println("Handler added: " + ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        server.getCallback().OnMessage(server, remoteClient, (Message) msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        server.getCallback().OnError(server, remoteClient, cause);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        this.remoteClient = new RemoteClient(ctx);
        this.server.getCallback().OnConnect(server, remoteClient);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        this.server.getCallback().OnDisconnect(server, remoteClient);
    }
}
