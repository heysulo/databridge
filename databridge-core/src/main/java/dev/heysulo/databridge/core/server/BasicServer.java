package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.common.StandardChannelInitializer;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import dev.heysulo.databridge.core.server.Server;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class BasicServer extends Server {
    public BasicServer(int port, ServerCallback callback) {
        super(port, callback);
    }

    public BasicServer(int port, ServerCallback callback, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        super(port, callback, bossGroup, workerGroup);
    }

    public void start() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.childHandler(new StandardChannelInitializer(this));
        start(bootstrap);
    }
}
