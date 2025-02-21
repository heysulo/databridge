package dev.heysulo.databridge.core.server.secure;

import dev.heysulo.databridge.core.common.StandardChannelInitializer;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.SslContext;

public class SecureServer extends Server {

    SslContext sslContext;

    public SecureServer(int port, ServerCallback callback, SslContext sslContext) {
        super(port, callback);
        this.sslContext = sslContext;
    }

    @Override
    public void start() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.childHandler(new StandardChannelInitializer(this));
        start(bootstrap);
    }

    public SslContext getSslContext() {
        return sslContext;
    }
}
