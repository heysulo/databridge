package dev.heysulo.databridge.core.common;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.ClientInboundHandler;
import dev.heysulo.databridge.core.server.BasicServer;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.ServerInboundHandler;
import dev.heysulo.databridge.core.server.secure.SecureServer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.List;

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
            channel.pipeline().addFirst("ssl", server.getSslContext().newHandler(channel.alloc()));
        } else if (useSsl) {
            channel.pipeline().addFirst("ssl", newClientSslHandler(channel));
        }
        List<String> trustedPackages = server != null ? server.getTrustedPackages()
                : client.getTrustedPackages();
        int hbInterval = server != null ? server.getHeartbeatInterval() : client.getHeartbeatInterval();
        int maxFrameLength = server != null ? server.getMaxFrameLength() : client.getMaxFrameLength();
        channel.pipeline().addLast("idleStateHandler",
                new io.netty.handler.timeout.IdleStateHandler(hbInterval * 2, 0, 0));
        channel.pipeline().addLast(
                new MessageDecoder(trustedPackages, maxFrameLength, true),
                new MessageEncoder(maxFrameLength),
                server != null ? new ServerInboundHandler(server) : new ClientInboundHandler(client));
    }

    private SslHandler newClientSslHandler(SocketChannel channel) {
        SslHandler sslHandler = client.getSslContext()
                .newHandler(channel.alloc(), client.getRemoteAddress(), client.getRemotePort());
        if (client.isHostnameVerificationEnabled()) {
            SSLEngine engine = sslHandler.engine();
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(parameters);
        }
        return sslHandler;
    }
}
