package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.ChannelConnection;
import dev.heysulo.databridge.core.common.EventLoops;
import dev.heysulo.databridge.core.common.ProtocolChannelInitializer;
import dev.heysulo.databridge.core.common.ProtocolSettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.Objects;

public class Client extends ChannelConnection {

    private static final String WORKER_THREAD_NAME = "databridge-client-worker";
    private static final String HOSTNAME_VERIFICATION_ALGORITHM = "HTTPS";

    private final ClientCallback callback;
    private final SslContext sslContext;
    private final boolean sharedWorkerGroup;
    private final ProtocolSettings settings = new ProtocolSettings();
    private EventLoopGroup workerGroup;
    private boolean hostnameVerificationEnabled = true;

    public Client(String remoteAddress, int remotePort, ClientCallback callback) {
        this(remoteAddress, remotePort, callback, null, null);
    }

    public Client(String remoteAddress, int remotePort, ClientCallback callback, EventLoopGroup workerGroup) {
        this(remoteAddress, remotePort, callback, null, workerGroup);
    }

    public Client(String remoteAddress, int remotePort, SslContext sslContext, ClientCallback callback) {
        this(remoteAddress, remotePort, callback, sslContext, null);
    }

    /**
     * Creates a client. A supplied {@code workerGroup} stays owned by the caller, and {@code stop()} leaves it running.
     *
     * @param sslContext  enables TLS, or {@code null} for a plain connection
     * @param workerGroup the event loops to share, or {@code null} for the client to manage its own
     */
    public Client(String remoteAddress, int remotePort, ClientCallback callback, SslContext sslContext,
                  EventLoopGroup workerGroup) {
        super(remoteAddress, remotePort);
        this.callback = Objects.requireNonNull(callback, "callback");
        this.sslContext = sslContext;
        this.workerGroup = workerGroup;
        this.sharedWorkerGroup = workerGroup != null;
    }

    public ProtocolSettings settings() {
        return settings;
    }

    public boolean isSharedWorkerGroup() {
        return sharedWorkerGroup;
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

    ClientCallback getCallback() {
        return callback;
    }

    public void connect() throws InterruptedException {
        if (workerGroup == null) {
            workerGroup = new NioEventLoopGroup(new DefaultThreadFactory(WORKER_THREAD_NAME));
        }
        Channel channel = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ProtocolChannelInitializer(
                        settings, this::configureTransportSecurity, () -> new ClientInboundHandler(this)))
                .connect(getRemoteAddress(), getRemotePort())
                .sync()
                .channel();
        attach(channel.pipeline().context(ClientInboundHandler.class));
    }

    private void configureTransportSecurity(SocketChannel channel) {
        if (sslContext == null) {
            return;
        }
        SslHandler sslHandler = sslContext.newHandler(channel.alloc(), getRemoteAddress(), getRemotePort());
        if (hostnameVerificationEnabled) {
            enableHostnameVerification(sslHandler.engine());
        }
        channel.pipeline().addFirst(sslHandler);
    }

    private static void enableHostnameVerification(SSLEngine engine) {
        SSLParameters parameters = engine.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm(HOSTNAME_VERIFICATION_ALGORITHM);
        engine.setSSLParameters(parameters);
    }

    /**
     * Closes the connection and releases owned resources. Waits for completion unless called from a callback, in
     * which case shutdown continues after the callback returns.
     */
    public void stop() throws InterruptedException {
        disconnect();
        if (sharedWorkerGroup || workerGroup == null) {
            return;
        }
        boolean calledFromEventLoop = EventLoops.isCurrentThreadIn(workerGroup);
        Future<?> shutdownFuture = workerGroup.shutdownGracefully();
        workerGroup = null;
        if (!calledFromEventLoop) {
            shutdownFuture.sync();
        }
    }
}
