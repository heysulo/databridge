package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.common.StandardChannelInitializer;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public abstract class Server {
    protected final int port;
    protected final ServerCallback callback;
    protected final EventLoopGroup bossGroup;
    protected final EventLoopGroup workerGroup;
    protected Class<? extends ServerSocketChannel> serverChannel = NioServerSocketChannel.class;

    public Server(int port, ServerCallback callback) {
        this(port, callback, null, null);
    }

    public Server(int port, ServerCallback callback, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        if (!validatePort(port)) {
            throw new IllegalArgumentException("Invalid port number");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }
        this.port = port;
        this.callback = callback;
        this.bossGroup = bossGroup == null ? new NioEventLoopGroup() : bossGroup;
        this.workerGroup = workerGroup == null ? new NioEventLoopGroup() : workerGroup;
    }

    private boolean validatePort(int port) {
        return port > 0 && port < 65535;
    }

    public void setServerChannel(Class<? extends ServerSocketChannel> serverChannel) {
        if (serverChannel == null) {
            throw new IllegalArgumentException("ServerChannel class cannot be null");
        }
        this.serverChannel = serverChannel;
    }

    public abstract void start() throws InterruptedException;

    protected void start(ServerBootstrap bootstrap) throws InterruptedException {
        bootstrap.channel(serverChannel)
                .group(bossGroup, workerGroup)
                .handler(new LoggingHandler(LogLevel.INFO)) // TODO: Make it configurable
                .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_SNDBUF, 1000 * 1024 * 1024) // TODO: Configure
                .option(ChannelOption.SO_RCVBUF, 1000 * 1024 * 1024)
                .bind(port)
                .sync();
    }

    public void stop() throws InterruptedException {
        bossGroup.shutdownGracefully().sync();
        workerGroup.shutdownGracefully().sync();
    }

    protected ServerCallback getCallback() {
        return callback;
    }
}
