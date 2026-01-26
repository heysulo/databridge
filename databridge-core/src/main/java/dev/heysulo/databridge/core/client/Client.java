package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.StandardChannelInitializer;
import dev.heysulo.databridge.core.exception.ConnectionBufferedException;
import dev.heysulo.databridge.core.exception.ConnectionUnavailableException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;

public class Client {
    protected final String remoteAddress;
    protected final int remotePort;
    protected final EventLoopGroup workerGroup;
    protected final SslContext sslContext;
    protected final java.util.List<String> trustedPackages = new java.util.ArrayList<>();
    protected ClientCallback callback;
    protected volatile ChannelHandlerContext channelHandlerContext;
    protected volatile long activeLatency = -1;
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
        this.workerGroup = workerGroup == null ? new NioEventLoopGroup(new DefaultThreadFactory("unnamed-client-worker-group")) : workerGroup;
        this.callback = callback;
        this.sslContext = sslContext;
        // Default allowed packages
        this.trustedPackages.add("dev.heysulo.**");
        this.trustedPackages.add("java.**");
    }

    public Client(ChannelHandlerContext channelHandlerContext) {
        this.remoteAddress = ((InetSocketAddress) channelHandlerContext.channel().remoteAddress()).getAddress()
                .getHostAddress();
        this.remotePort = ((InetSocketAddress) channelHandlerContext.channel().remoteAddress()).getPort();
        this.callback = null;
        this.workerGroup = null;
        this.channelHandlerContext = channelHandlerContext;
        this.sslContext = null; // check
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

    public void send(Message message) {
        if (!channelHandlerContext.channel().isActive()) {
            throw new ConnectionUnavailableException(channelHandlerContext.channel());
        }
        if (!channelHandlerContext.channel().isWritable()) {
            throw new ConnectionBufferedException();
        }
        channelHandlerContext.writeAndFlush(message);
    }

    public void disconnect() throws InterruptedException {
        if (channelHandlerContext != null) {
            channelHandlerContext.close().sync();
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
