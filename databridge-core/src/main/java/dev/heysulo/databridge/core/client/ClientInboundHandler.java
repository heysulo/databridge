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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        this.client.getCallback().OnMessage(client, (Message) msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        this.client.getCallback().OnError(client, cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.client.channelHandlerContext = ctx;
        this.client.getCallback().OnConnect(client);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        this.client.getCallback().OnDisconnect(client);
    }
}
