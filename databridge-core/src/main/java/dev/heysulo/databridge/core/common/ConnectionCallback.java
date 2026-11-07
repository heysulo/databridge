package dev.heysulo.databridge.core.common;

/**
 * Receives the events of one connection, in place of the server's or client's callback. Install it with
 * {@link Connection#setCallback(ConnectionCallback)}. Like other callbacks, it runs on a Netty event loop, so blocking
 * here delays every connection on that loop.
 */
public interface ConnectionCallback {
    void onDisconnect(Connection connection);

    void onMessage(Connection connection, Message message);

    void onError(Connection connection, Throwable cause);
}
