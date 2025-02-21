package dev.heysulo.databridge.core.server.callback;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.Server;

public interface ServerCallback {
    public void OnConnect(Server server, Client client);

    public void OnDisconnect(Server server, Client client);

    public void OnMessage(Server server, Client client, Message msg);

    public void OnError(Server server, Client client, Throwable cause);
}
