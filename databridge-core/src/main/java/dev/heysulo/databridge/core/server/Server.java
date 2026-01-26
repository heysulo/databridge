package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultThreadFactory;

public abstract class Server {
    protected final int port;
    protected final ServerCallback callback;
    protected final EventLoopGroup bossGroup;
    protected final EventLoopGroup workerGroup;
    protected final java.util.List<String> trustedPackages = new java.util.ArrayList<>();
    // Metrics
    protected final java.util.concurrent.atomic.AtomicLong activeConnections = new java.util.concurrent.atomic.AtomicLong();
    protected final java.util.concurrent.atomic.AtomicLong totalMessages = new java.util.concurrent.atomic.AtomicLong();
    protected Class<? extends ServerSocketChannel> serverChannel = NioServerSocketChannel.class;
    protected volatile long activeLatency = -1; // -1 means unknown
    // Config
    protected int heartbeatInterval = 30;
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
        this.bossGroup = bossGroup == null
                ? new NioEventLoopGroup(new DefaultThreadFactory("unnamed-server-boss-group"))
                : bossGroup;

        this.workerGroup = workerGroup == null
                ? new NioEventLoopGroup(new DefaultThreadFactory("unnamed-server-client-worker-group"))
                : workerGroup;
        // Default allowed packages
        this.trustedPackages.add("dev.heysulo.**");
        this.trustedPackages.add("java.**");
    }

    public long getLatency() {
        return activeLatency;
    }

    public void updateLatency(long latency) {
        this.activeLatency = latency;
    }

    public int getHeartbeatInterval() {
        return this.heartbeatInterval;
    }

    public void setHeartbeatInterval(int seconds) {
        this.heartbeatInterval = seconds;
    }

    public void addTrustedPackage(String packagePattern) {
        this.trustedPackages.add(packagePattern);
    }

    public java.util.List<String> getTrustedPackages() {
        return trustedPackages;
    }

    public long getActiveConnections() {
        return activeConnections.get();
    }

    public long getTotalMessages() {
        return totalMessages.get();
    }

    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementConnections() {
        activeConnections.decrementAndGet();
    }

    public void incrementMessages() {
        totalMessages.incrementAndGet();
    }

    public boolean validatePort(int port) {
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

    public SslContext getSslContext() {
        return null; // Default implementation returns null
    }
}
