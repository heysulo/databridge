package dev.heysulo.databridge.core.common;

import dev.heysulo.databridge.core.exception.ConnectionBufferedException;
import dev.heysulo.databridge.core.exception.ConnectionUnavailableException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;
import java.util.OptionalLong;

/**
 * A {@link Connection} backed by a Netty channel.
 */
public abstract class ChannelConnection implements Connection {

    private final String remoteAddress;
    private final int remotePort;
    private volatile ChannelHandlerContext context;
    private volatile OptionalLong latencyMillis = OptionalLong.empty();
    private volatile ConnectionCallback callback;

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

    final ConnectionCallback getCallback() {
        return callback;
    }

    @Override
    public void setCallback(ConnectionCallback callback) {
        this.callback = callback;
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
    public boolean isSecure() {
        SslHandler handler = sslHandler();
        return handler != null && handler.handshakeFuture().isSuccess();
    }

    @Override
    public List<Certificate> getPeerCertificates() {
        SslHandler handler = sslHandler();
        if (handler == null || !handler.handshakeFuture().isSuccess()) {
            return List.of();
        }
        SSLSession session = handler.engine().getSession();
        try {
            return List.of(session.getPeerCertificates());
        } catch (SSLPeerUnverifiedException e) {
            return List.of();
        }
    }

    private SslHandler sslHandler() {
        ChannelHandlerContext current = context;
        return current == null ? null : current.pipeline().get(SslHandler.class);
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
