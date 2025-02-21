package dev.heysulo.databridge.core.common;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.ClientInboundHandler;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.ServerInboundHandler;
import dev.heysulo.databridge.core.server.BasicServer;
import dev.heysulo.databridge.core.server.secure.SecureServer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class StandardChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final Server server;
    private final Client client;
    private final boolean useSsl;

    private StandardChannelInitializer(Server server, Client client, boolean useSsl) {
        this.server = server;
        this.client = client;
        this.useSsl = useSsl;
    }

    public StandardChannelInitializer(Client client) {
        this(null, client, client.getSslContext() != null);
    }

    public StandardChannelInitializer(BasicServer server) {
        this(server, null, false);
    }

    public StandardChannelInitializer(SecureServer server) {
        this(server, null, true);
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        if (useSsl && client == null) {
            channel.pipeline().addFirst("ssl", ((SecureServer)server).getSslContext().newHandler(channel.alloc()));
        } else if (useSsl) {
            channel.pipeline().addFirst("ssl", client.getSslContext().newHandler(channel.alloc()));
        }
        channel.pipeline().addLast(
                new MessageDecoder(),
                new MessageEncoder(),
                server != null ? new ServerInboundHandler(server) : new ClientInboundHandler(client)
        );
    }
}
