package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.channel.EventLoopGroup;

public class BasicServer extends Server {
    public BasicServer(int port, ServerCallback callback) {
        super(port, callback);
    }

    public BasicServer(int port, ServerCallback callback, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        super(port, callback, bossGroup, workerGroup);
    }
}
