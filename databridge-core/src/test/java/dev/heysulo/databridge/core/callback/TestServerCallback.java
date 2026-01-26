package dev.heysulo.databridge.core.callback;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;

public class TestServerCallback implements ServerCallback {
    public boolean connected = false;
    public boolean disconnected = false;
    public boolean errorReceived = false;
    public Throwable exceptionCaught;
    public java.util.List<Message> messageReceived = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());
    public java.util.List<Client> connectedClients = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());

    @Override
    public void OnConnect(Server server, Client client) {
        connected = true;
        connectedClients.add(client);
    }

    @Override
    public void OnDisconnect(Server server, Client client) {
        disconnected = true;
    }

    @Override
    public void OnMessage(Server server, Client client, Message msg) {
        messageReceived.add(msg);
    }

    @Override
    public void OnError(Server server, Client client, Throwable cause) {
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
