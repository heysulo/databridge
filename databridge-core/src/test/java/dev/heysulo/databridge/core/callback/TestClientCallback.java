package dev.heysulo.databridge.core.callback;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestClientCallback implements ClientCallback {
    public volatile boolean connected = false;
    public volatile boolean disconnected = false;
    public final List<Message> messageReceived = Collections.synchronizedList(new ArrayList<>());
    public volatile boolean errorReceived = false;

    @Override
    public void onConnect(Client client) {
        connected = true;
    }

    @Override
    public void onDisconnect(Client client) {
        disconnected = true;
    }

    @Override
    public void onMessage(Client client, Message msg) {
        messageReceived.add(msg);
    }

    @Override
    public void onError(Client client, Throwable cause) {
        errorReceived = true;
    }

    public void reset() {
        connected = false;
        disconnected = false;
        errorReceived = false;
        messageReceived.clear();
    }
}
