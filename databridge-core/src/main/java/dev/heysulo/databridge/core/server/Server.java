package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.MessageDecoder;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Server {
    public static final List<String> DEFAULT_TRUSTED_PACKAGES = List.of(
            "!java.util.PriorityQueue",
            "!java.rmi.**",
            "!java.lang.reflect.**",
            "!java.lang.invoke.**",
            "dev.heysulo.**",
            "java.lang.*",
            "java.util.*"
    );

    protected final int port;
    protected final ServerCallback callback;
    protected final EventLoopGroup bossGroup;
    protected final EventLoopGroup workerGroup;
    private final boolean sharedBossGroup;
    private final boolean sharedWorkerGroup;
    protected volatile Channel serverChannelInstance;
    protected final List<String> trustedPackages = new CopyOnWriteArrayList<>(DEFAULT_TRUSTED_PACKAGES);
    protected int maxFrameLength = MessageDecoder.DEFAULT_MAX_FRAME_LENGTH;

    // Per-client tracking and metrics
    protected final Set<Client> connectedClients = ConcurrentHashMap.newKeySet();
    protected final AtomicLong activeConnections = new AtomicLong();
    protected final AtomicLong totalMessages = new AtomicLong();
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
        this.sharedBossGroup = bossGroup != null;
        this.sharedWorkerGroup = workerGroup != null;
        this.bossGroup = sharedBossGroup
                ? bossGroup
                : new NioEventLoopGroup(new DefaultThreadFactory("unnamed-server-boss-group"));

        this.workerGroup = sharedWorkerGroup
                ? workerGroup
                : new NioEventLoopGroup(new DefaultThreadFactory("unnamed-server-client-worker-group"));
    }

    public boolean isSharedBossGroup() {
        return sharedBossGroup;
    }

    public boolean isSharedWorkerGroup() {
        return sharedWorkerGroup;
    }

    public void addConnectedClient(Client client) {
        if (client != null) {
            connectedClients.add(client);
        }
    }

    public void removeConnectedClient(Client client) {
        if (client != null) {
            connectedClients.remove(client);
        }
    }

    public Collection<Client> getConnectedClients() {
        return Collections.unmodifiableCollection(connectedClients);
    }

    public long getAverageLatency() {
        long total = 0;
        int count = 0;
        for (Client client : connectedClients) {
            long lat = client.getLatency();
            if (lat >= 0) {
                total += lat;
                count++;
            }
        }
        return count > 0 ? (total / count) : -1;
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

    public List<String> getTrustedPackages() {
        return trustedPackages;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public void setMaxFrameLength(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
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
        return port > 0 && port <= 65535;
    }

    public void setServerChannel(Class<? extends ServerSocketChannel> serverChannel) {
        if (serverChannel == null) {
            throw new IllegalArgumentException("ServerChannel class cannot be null");
        }
        this.serverChannel = serverChannel;
    }

    public abstract void start() throws InterruptedException;

    protected void start(ServerBootstrap bootstrap) throws InterruptedException {
        this.serverChannelInstance = bootstrap.channel(serverChannel)
                .group(bossGroup, workerGroup)
                .handler(new LoggingHandler(LogLevel.DEBUG)) // TODO: Make it configurable
                .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .bind(port)
                .sync()
                .channel();
    }

    public void stop() throws InterruptedException {
        if (serverChannelInstance != null) {
            serverChannelInstance.close().sync();
        }
        if (!sharedBossGroup && bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
        if (!sharedWorkerGroup && workerGroup != null) {
            workerGroup.shutdownGracefully().sync();
        }
    }

    protected ServerCallback getCallback() {
        return callback;
    }

    public SslContext getSslContext() {
        return null; // Default implementation returns null
    }
}
