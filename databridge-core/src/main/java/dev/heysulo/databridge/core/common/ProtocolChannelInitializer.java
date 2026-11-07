package dev.heysulo.databridge.core.common;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds the pipeline shared by clients and servers.
 */
public final class ProtocolChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int HEARTBEATS_MISSED_BEFORE_IDLE = 2;

    private final ProtocolSettings settings;
    private final Consumer<SocketChannel> transportSecurity;
    private final Supplier<ChannelHandler> protocolHandler;

    public ProtocolChannelInitializer(ProtocolSettings settings,
                                      Consumer<SocketChannel> transportSecurity,
                                      Supplier<ChannelHandler> protocolHandler) {
        this.settings = settings;
        this.transportSecurity = transportSecurity;
        this.protocolHandler = protocolHandler;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        transportSecurity.accept(channel);
        long readerIdleMillis = settings.getHeartbeatInterval().toMillis() * HEARTBEATS_MISSED_BEFORE_IDLE;
        int maxFrameLength = settings.getMaxFrameLength();
        channel.pipeline().addLast(
                new IdleStateHandler(readerIdleMillis, 0, 0, TimeUnit.MILLISECONDS),
                new MessageDecoder(settings.getTrustedPackages(), maxFrameLength, true),
                new MessageEncoder(maxFrameLength),
                protocolHandler.get());
    }
}
