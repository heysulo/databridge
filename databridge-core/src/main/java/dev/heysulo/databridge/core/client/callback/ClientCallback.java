package dev.heysulo.databridge.core.client.callback;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;

/**
 * Receives client events on a Netty event loop. Blocking here delays every connection on that loop.
 */
public interface ClientCallback {
    void onConnect(Client client);

    void onDisconnect(Client client);

    void onMessage(Client client, Message message);

    void onError(Client client, Throwable cause);
}
