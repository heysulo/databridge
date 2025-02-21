package dev.heysulo.databridge.core.client;

import io.netty.channel.ChannelHandlerContext;

public class RemoteClient extends Client {

    public RemoteClient(ChannelHandlerContext context) {
        super(context);
        this.channelHandlerContext = context;
    }
}
