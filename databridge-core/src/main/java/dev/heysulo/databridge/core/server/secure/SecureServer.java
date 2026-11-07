package dev.heysulo.databridge.core.server.secure;

import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;

import java.util.Objects;

public class SecureServer extends Server {

    private final SslContext sslContext;

    public SecureServer(int port, ServerCallback callback, SslContext sslContext) {
        this(port, callback, sslContext, null, null);
    }

    public SecureServer(int port, ServerCallback callback, SslContext sslContext,
                        EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        super(port, callback, bossGroup, workerGroup);
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
    }

    @Override
    protected void configureTransportSecurity(SocketChannel channel) {
        channel.pipeline().addFirst(sslContext.newHandler(channel.alloc()));
    }
}
