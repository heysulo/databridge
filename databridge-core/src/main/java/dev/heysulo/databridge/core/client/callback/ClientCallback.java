package dev.heysulo.databridge.core.client.callback;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;

public interface ClientCallback {
    public void OnConnect(Client client);

    public void OnDisconnect(Client client);

    public void OnMessage(Client client, Message msg);

    public void OnError(Client client, Throwable cause);
}
