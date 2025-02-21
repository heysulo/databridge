package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.StandardChannelInitializer;
import dev.heysulo.databridge.core.exception.ConnectionBufferedException;
import dev.heysulo.databridge.core.exception.ConnectionUnavailableException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.InetSocketAddress;

public class Client {
    protected final String remoteAddress;
    protected final int remotePort;
    protected final ClientCallback callback;
    protected final EventLoopGroup workerGroup;
    protected ChannelHandlerContext channelHandlerContext;
    protected final SslContext sslContext;

    public Client(String remoteAddress, int remotePort, ClientCallback callback) {
        this(remoteAddress, remotePort, null, null, callback);
    }

    public Client(String remoteAddress, int remotePort, SslContext sslContext, ClientCallback callback) {
        this(remoteAddress, remotePort, null, sslContext, callback);
    }

    public Client(String remoteAddress, int remotePort, EventLoopGroup workerGroup, SslContext sslContext, ClientCallback callback) {
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        this.workerGroup = workerGroup == null ? new NioEventLoopGroup() : workerGroup;
        this.callback = callback;
        this.sslContext = sslContext;
    }

    public Client(ChannelHandlerContext channelHandlerContext) {
        this.remoteAddress = ((InetSocketAddress) channelHandlerContext.channel().remoteAddress()).getAddress().getHostAddress();
        this.remotePort = ((InetSocketAddress) channelHandlerContext.channel().remoteAddress()).getPort();
        this.callback = null;
        this.workerGroup = null;
        this.channelHandlerContext = channelHandlerContext;
        this.sslContext = null; // check
    }

    public void connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        Channel channel = bootstrap
                .option(ChannelOption.SO_SNDBUF, 1000 * 1024 * 1024) // TODO: Configure
                .option(ChannelOption.SO_RCVBUF, 1000 * 1024 * 1024)
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new StandardChannelInitializer(this))
                .connect(remoteAddress, remotePort)
                .sync()
                .channel();
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
        channelHandlerContext.close().sync();
    }

    public ClientCallback getCallback() {
        return callback;
    }

    public SslContext getSslContext() {
        return sslContext;
    }
}
