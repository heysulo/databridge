package dev.heysulo.databridge.core.callback;

import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;

public class TestServerCallback implements ServerCallback {
    public volatile boolean connected = false;
    public volatile boolean disconnected = false;
    public volatile boolean errorReceived = false;
    public volatile Throwable exceptionCaught;
    public java.util.List<Message> messageReceived = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());
    public java.util.List<Connection> connectedClients = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());

    @Override
    public void onConnect(Server server, Connection client) {
        connected = true;
        connectedClients.add(client);
    }

    @Override
    public void onDisconnect(Server server, Connection client) {
        disconnected = true;
    }

    @Override
    public void onMessage(Server server, Connection client, Message msg) {
        messageReceived.add(msg);
    }

    @Override
    public void onError(Server server, Connection client, Throwable cause) {
        errorReceived = true;
        exceptionCaught = cause;
    }

    public void reset() {
        connectedClients.forEach(client -> {
            try {
                client.disconnect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve the interrupted status
                throw new RuntimeException("Failed to disconnect client", e);
            }
        });
        connected = false;
        disconnected = false;
        errorReceived = false;
        messageReceived.clear();
        connectedClients.clear();
    }
}
