package dev.heysulo.databridge.core.server.callback;

import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.Server;

/**
 * Receives server events on a Netty event loop. Blocking here delays every connection on that loop.
 */
public interface ServerCallback {
    void onConnect(Server server, Connection connection);

    void onDisconnect(Server server, Connection connection);

    void onMessage(Server server, Connection connection, Message message);

    void onError(Server server, Connection connection, Throwable cause);
}
