package dev.heysulo.databridge.core.callback;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;

import java.util.ArrayList;

public class TestClientCallback implements ClientCallback {
    public boolean connected = false;
    public boolean disconnected = false;
    public ArrayList<Message> messageReceived = new ArrayList<>();
    public boolean errorReceived = false;

    @Override
    public void OnConnect(Client client) {
        connected = true;
    }

    @Override
    public void OnDisconnect(Client client) {
        disconnected = true;
    }

    @Override
    public void OnMessage(Client client, Message msg) {
        messageReceived.add(msg);
    }

    @Override
    public void OnError(Client client, Throwable cause) {
        errorReceived = true;
    }

    public void reset() {
        connected = false;
        disconnected = false;
        errorReceived = false;
        messageReceived.clear();
    }
}
