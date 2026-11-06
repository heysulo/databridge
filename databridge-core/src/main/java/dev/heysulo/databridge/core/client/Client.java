package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.EventLoops;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.MessageDecoder;
import dev.heysulo.databridge.core.common.StandardChannelInitializer;
import dev.heysulo.databridge.core.exception.ConnectionBufferedException;
import dev.heysulo.databridge.core.exception.ConnectionUnavailableException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Client {
    public static final List<String> DEFAULT_TRUSTED_PACKAGES = MessageDecoder.DEFAULT_TRUSTED_PACKAGES;

    protected final String remoteAddress;
    protected final int remotePort;
    protected final EventLoopGroup workerGroup;
    private final boolean sharedWorkerGroup;
    protected final SslContext sslContext;
    protected final List<String> trustedPackages = new CopyOnWriteArrayList<>(DEFAULT_TRUSTED_PACKAGES);
    protected ClientCallback callback;
    protected volatile ChannelHandlerContext channelHandlerContext;
    protected volatile long activeLatency = -1;
    protected int maxFrameLength = MessageDecoder.DEFAULT_MAX_FRAME_LENGTH;
    protected boolean hostnameVerificationEnabled = true;

    // Config
    protected int heartbeatInterval = 30;

    public Client(String remoteAddress, int remotePort, ClientCallback callback) {
        this(remoteAddress, remotePort, null, null, callback);
    }

    public Client(String remoteAddress, int remotePort, ClientCallback callback, EventLoopGroup workerGroup) {
        this(remoteAddress, remotePort, workerGroup, null, callback);
    }

    public Client(String remoteAddress, int remotePort, SslContext sslContext, ClientCallback callback) {
        this(remoteAddress, remotePort, null, sslContext, callback);
    }

    public Client(String remoteAddress, int remotePort, EventLoopGroup workerGroup, SslContext sslContext,
                  ClientCallback callback) {
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        this.sharedWorkerGroup = workerGroup != null;
        this.workerGroup = sharedWorkerGroup
                ? workerGroup
                : new NioEventLoopGroup(new DefaultThreadFactory("unnamed-client-worker-group"));
        this.callback = callback;
        this.sslContext = sslContext;
    }

    public Client(ChannelHandlerContext channelHandlerContext) {
        this.remoteAddress = ((InetSocketAddress) channelHandlerContext.channel().remoteAddress()).getAddress()
                .getHostAddress();
        this.remotePort = ((InetSocketAddress) channelHandlerContext.channel().remoteAddress()).getPort();
        this.callback = null;
        this.sharedWorkerGroup = true; // Managed by server/pipeline
        this.workerGroup = null;
        this.channelHandlerContext = channelHandlerContext;
        this.sslContext = null;
    }

    public boolean isSharedWorkerGroup() {
        return sharedWorkerGroup;
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

    public boolean isHostnameVerificationEnabled() {
        return hostnameVerificationEnabled;
    }

    /**
     * Controls whether TLS connections check that the server certificate matches the remote address. Disabling this
     * allows impersonation by anyone holding a trusted certificate, so it should be limited to controlled environments.
     */
    public void setHostnameVerificationEnabled(boolean hostnameVerificationEnabled) {
        this.hostnameVerificationEnabled = hostnameVerificationEnabled;
    }

    public void connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        Channel channel = bootstrap
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new StandardChannelInitializer(this))
                .connect(remoteAddress, remotePort)
                .sync()
                .channel();
        this.channelHandlerContext = channel.pipeline().context(ClientInboundHandler.class);
    }

    /**
     * Queues a message for delivery. Failures after this call returns, such as a message that cannot be serialized,
     * are reported through the {@code OnError} callback.
     */
    public void send(Message message) {
        if (channelHandlerContext == null || !channelHandlerContext.channel().isActive()) {
            throw new ConnectionUnavailableException(channelHandlerContext != null ? channelHandlerContext.channel() : null);
        }
        if (!channelHandlerContext.channel().isWritable()) {
            throw new ConnectionBufferedException();
        }
        channelHandlerContext.writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public boolean isConnected() {
        return channelHandlerContext != null && channelHandlerContext.channel().isActive();
    }

    /**
     * Closes the connection. Waits for completion unless called from a callback, where waiting is not possible.
     */
    public void disconnect() throws InterruptedException {
        ChannelHandlerContext context = channelHandlerContext;
        if (context == null) {
            return;
        }
        ChannelFuture closeFuture = context.close();
        if (!context.executor().inEventLoop()) {
            closeFuture.sync();
        }
    }

    /**
     * Closes the connection and releases owned resources. Waits for completion unless called from a callback, in
     * which case shutdown continues after the callback returns.
     */
    public void stop() throws InterruptedException {
        disconnect();
        if (!sharedWorkerGroup && workerGroup != null) {
            Future<?> shutdownFuture = workerGroup.shutdownGracefully();
            if (!EventLoops.isCurrentThreadIn(workerGroup)) {
                shutdownFuture.sync();
            }
        }
    }

    public ClientCallback getCallback() {
        return callback;
    }

    public void setCallback(ClientCallback callback) {
        this.callback = callback;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }
}
