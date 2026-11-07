package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.common.ChannelConnection;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * The server's side of a connection to a client.
 */
final class RemoteConnection extends ChannelConnection {

    private static final int UNKNOWN_PORT = -1;

    RemoteConnection(Channel channel) {
        this(channel.remoteAddress());
    }

    private RemoteConnection(SocketAddress remoteAddress) {
        super(hostOf(remoteAddress), portOf(remoteAddress));
    }

    private static String hostOf(SocketAddress address) {
        return address instanceof InetSocketAddress
                ? ((InetSocketAddress) address).getAddress().getHostAddress()
                : String.valueOf(address);
    }

    private static int portOf(SocketAddress address) {
        return address instanceof InetSocketAddress ? ((InetSocketAddress) address).getPort() : UNKNOWN_PORT;
    }
}
