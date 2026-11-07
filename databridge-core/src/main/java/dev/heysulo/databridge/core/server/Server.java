package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.EventLoops;
import dev.heysulo.databridge.core.common.ProtocolChannelInitializer;
import dev.heysulo.databridge.core.common.ProtocolSettings;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    private static final int MAX_PORT = 65535;
    private static final String BOSS_THREAD_NAME = "databridge-server-boss";
    private static final String WORKER_THREAD_NAME = "databridge-server-worker";

    private final int port;
    private final ServerCallback callback;
    private final boolean sharedBossGroup;
    private final boolean sharedWorkerGroup;
    private final ProtocolSettings settings = new ProtocolSettings();
    private final ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicLong activeConnections = new AtomicLong();
    private final AtomicLong totalMessages = new AtomicLong();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel listenerChannel;
    private Class<? extends ServerSocketChannel> channelType = NioServerSocketChannel.class;

    protected Server(int port, ServerCallback callback) {
        this(port, callback, null, null);
    }

    /**
     * Creates a server. Supplied event loop groups stay owned by the caller, and {@code stop()} leaves them running.
     *
     * @param bossGroup   the event loops accepting connections, or {@code null} for the server to manage its own
     * @param workerGroup the event loops serving connections, or {@code null} for the server to manage its own
     */
    protected Server(int port, ServerCallback callback, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        if (port <= 0 || port > MAX_PORT) {
            throw new IllegalArgumentException("Invalid port number");
        }
        this.port = port;
        this.callback = Objects.requireNonNull(callback, "callback");
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
        this.sharedBossGroup = bossGroup != null;
        this.sharedWorkerGroup = workerGroup != null;
    }

    public ProtocolSettings settings() {
        return settings;
    }

    public boolean isSharedBossGroup() {
        return sharedBossGroup;
    }

    public boolean isSharedWorkerGroup() {
        return sharedWorkerGroup;
    }

    public Collection<Connection> getConnections() {
        return Collections.unmodifiableCollection(connections);
    }

    /**
     * Returns the mean latency of connections that have measured one, or empty if none have.
     */
    public OptionalLong getAverageLatencyMillis() {
        LongSummaryStatistics latencies = connections.stream()
                .map(Connection::getLatencyMillis)
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .summaryStatistics();
        return latencies.getCount() > 0
                ? OptionalLong.of(latencies.getSum() / latencies.getCount())
                : OptionalLong.empty();
    }

    public long getActiveConnections() {
        return activeConnections.get();
    }

    public long getTotalMessages() {
        return totalMessages.get();
    }

    public void setChannelType(Class<? extends ServerSocketChannel> channelType) {
        this.channelType = Objects.requireNonNull(channelType, "channelType");
    }

    ServerCallback getCallback() {
        return callback;
    }

    void channelOpened(Channel channel) {
        activeConnections.incrementAndGet();
        clientChannels.add(channel);
    }

    void connectionEstablished(Connection connection) {
        connections.add(connection);
    }

    void messageReceived() {
        totalMessages.incrementAndGet();
    }

    void channelClosed(Connection connection) {
        activeConnections.decrementAndGet();
        connections.remove(connection);
    }

    /**
     * Installs any security handlers on a new connection. Plain servers install none.
     */
    protected void configureTransportSecurity(SocketChannel channel) {
    }

    public void start() throws InterruptedException {
        if (bossGroup == null) {
            bossGroup = new NioEventLoopGroup(new DefaultThreadFactory(BOSS_THREAD_NAME));
        }
        if (workerGroup == null) {
            workerGroup = new NioEventLoopGroup(new DefaultThreadFactory(WORKER_THREAD_NAME));
        }
        listenerChannel = new ServerBootstrap()
                .channel(channelType)
                .group(bossGroup, workerGroup)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ProtocolChannelInitializer(
                        settings, this::configureTransportSecurity, () -> new ServerInboundHandler(this)))
                .bind(port)
                .sync()
                .channel();
        LOGGER.info("Listening on port {}", port);
    }

    /**
     * Stops listening, closes every client connection and releases owned resources. Waits for completion unless
     * called from a callback, in which case shutdown continues after the callback returns.
     */
    public void stop() throws InterruptedException {
        if (listenerChannel == null) {
            return;
        }
        boolean canWait = !EventLoops.isCurrentThreadIn(bossGroup) && !EventLoops.isCurrentThreadIn(workerGroup);
        awaitIf(canWait, listenerChannel.close());
        awaitIf(canWait, clientChannels.close());
        if (!sharedBossGroup) {
            awaitIf(canWait, bossGroup.shutdownGracefully());
            bossGroup = null;
        }
        if (!sharedWorkerGroup) {
            awaitIf(canWait, workerGroup.shutdownGracefully());
            workerGroup = null;
        }
        listenerChannel = null;
        LOGGER.info("Stopped listening on port {}", port);
    }

    private static void awaitIf(boolean canWait, Future<?> future) throws InterruptedException {
        if (canWait) {
            future.await();
        }
    }
}
