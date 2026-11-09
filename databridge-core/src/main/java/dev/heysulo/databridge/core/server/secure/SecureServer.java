package dev.heysulo.databridge.core.server.secure;

import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A server that secures its connections with TLS.
 * <p>
 * The context can be supplied rather than fixed, so certificates can be renewed while the server runs: each new
 * connection uses the context the supplier returns when it is accepted, and existing connections keep theirs.
 * <p>
 * A server can also accept plain connections on the same port, to move clients to TLS one at a time. It tells the two
 * apart by the first bytes a client sends. Plain connections are not secure, so allow them only while migrating.
 */
public class SecureServer extends Server {

    // A TLS record header is five bytes: enough to tell a TLS client from a plain one
    private static final int TLS_RECORD_HEADER_LENGTH = 5;

    private final Supplier<SslContext> sslContext;
    private final boolean plaintextAllowed;

    public SecureServer(int port, ServerCallback callback, SslContext sslContext) {
        this(port, callback, sslContext, null, null);
    }

    public SecureServer(int port, ServerCallback callback, SslContext sslContext,
                        EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        this(port, callback, fixed(sslContext), false, bossGroup, workerGroup);
    }

    /**
     * Creates a server whose TLS context may change while it runs.
     *
     * @param sslContext       returns the context for each new connection; called on an event loop, so keep it fast
     * @param plaintextAllowed whether clients may also connect without TLS on the same port
     * @param bossGroup        the event loops accepting connections, or {@code null} for the server to manage its own
     * @param workerGroup      the event loops serving connections, or {@code null} for the server to manage its own
     */
    public SecureServer(int port, ServerCallback callback, Supplier<SslContext> sslContext, boolean plaintextAllowed,
                        EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        super(port, callback, bossGroup, workerGroup);
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
        this.plaintextAllowed = plaintextAllowed;
    }

    private static Supplier<SslContext> fixed(SslContext sslContext) {
        Objects.requireNonNull(sslContext, "sslContext");
        return () -> sslContext;
    }

    public boolean isPlaintextAllowed() {
        return plaintextAllowed;
    }

    @Override
    protected void configureTransportSecurity(SocketChannel channel) {
        if (plaintextAllowed) {
            channel.pipeline().addFirst(new TlsDetector());
        } else {
            channel.pipeline().addFirst(newSslHandler(channel));
        }
    }

    private SslHandler newSslHandler(SocketChannel channel) {
        SslContext context = Objects.requireNonNull(sslContext.get(), "The supplied SslContext is null");
        return context.newHandler(channel.alloc());
    }

    /** Waits for a client's first bytes, then secures the connection if they start a TLS handshake. */
    private final class TlsDetector extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < TLS_RECORD_HEADER_LENGTH) {
                return;
            }
            if (SslHandler.isEncrypted(in)) {
                context.pipeline().replace(this, null, newSslHandler((SocketChannel) context.channel()));
            } else {
                context.pipeline().remove(this);
            }
        }
    }
}
