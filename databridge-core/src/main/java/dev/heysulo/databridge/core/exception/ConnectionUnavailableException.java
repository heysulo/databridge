package dev.heysulo.databridge.core.exception;

import io.netty.channel.Channel;

public class ConnectionUnavailableException extends RuntimeException {
    public ConnectionUnavailableException(Channel channel) {
        super(channel != null
                ? String.format("Id: %s, LocalAddress: %s, RemoteAddress: %s",
                        channel.id().asShortText(),
                        channel.localAddress(),
                        channel.remoteAddress())
                : "Channel is unavailable (null or inactive)");
    }
}
