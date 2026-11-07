package dev.heysulo.databridge.core.common;

import dev.heysulo.databridge.core.exception.ConnectionBufferedException;
import dev.heysulo.databridge.core.exception.ConnectionUnavailableException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.OptionalLong;

/**
 * A {@link Connection} backed by a Netty channel.
 */
public abstract class ChannelConnection implements Connection {

    private final String remoteAddress;
    private final int remotePort;
    private volatile ChannelHandlerContext context;
    private volatile OptionalLong latencyMillis = OptionalLong.empty();

    protected ChannelConnection(String remoteAddress, int remotePort) {
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    protected final void attach(ChannelHandlerContext context) {
        if (context != null) {
            this.context = context;
        }
    }

    final void recordLatency(long millis) {
        this.latencyMillis = OptionalLong.of(millis);
    }

    @Override
    public void send(Message message) {
        ChannelHandlerContext current = context;
        if (current == null || !current.channel().isActive()) {
            throw new ConnectionUnavailableException(current != null ? current.channel() : null);
        }
        if (!current.channel().isWritable()) {
            throw new ConnectionBufferedException();
        }
        current.writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    @Override
    public boolean isConnected() {
        ChannelHandlerContext current = context;
        return current != null && current.channel().isActive();
    }

    @Override
    public void disconnect() throws InterruptedException {
        ChannelHandlerContext current = context;
        if (current == null) {
            return;
        }
        ChannelFuture closeFuture = current.close();
        if (!current.executor().inEventLoop()) {
            closeFuture.sync();
        }
    }

    @Override
    public OptionalLong getLatencyMillis() {
        return latencyMillis;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public int getRemotePort() {
        return remotePort;
    }
}
