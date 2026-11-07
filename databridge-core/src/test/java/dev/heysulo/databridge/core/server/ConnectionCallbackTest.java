package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.ConnectionCallback;
import dev.heysulo.databridge.core.common.Message;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

public class ConnectionCallbackTest {

    private static final String SERVER_ADDRESS = "127.0.0.1";

    public static class Note implements Message {
    }

    public static class NotSerializable implements Message {
        public final Object field = new Object();
    }

    private static class RecordingCallback implements ConnectionCallback {
        final List<Message> messages = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<Connection> disconnects = new CopyOnWriteArrayList<>();

        @Override
        public void onDisconnect(Connection connection) {
            disconnects.add(connection);
        }

        @Override
        public void onMessage(Connection connection, Message message) {
            messages.add(message);
        }

        @Override
        public void onError(Connection connection, Throwable cause) {
            errors.add(cause);
        }
    }

    private static class CountingServerCallback extends TestServerCallback {
        final AtomicInteger disconnects = new AtomicInteger();

        @Override
        public void onDisconnect(Server server, Connection connection) {
            disconnects.incrementAndGet();
            super.onDisconnect(server, connection);
        }
    }

    private CountingServerCallback serverCallback;
    private TestClientCallback clientCallback;
    private BasicServer server;
    private Client client;

    @BeforeMethod
    public void connect() throws Exception {
        int port = freePort();
        serverCallback = new CountingServerCallback();
        clientCallback = new TestClientCallback();
        server = new BasicServer(port, serverCallback);
        server.start();
        client = new Client(SERVER_ADDRESS, port, clientCallback);
        client.connect();
        waitUntil(() -> serverCallback.connected && clientCallback.connected, "both sides connect");
    }

    @AfterMethod(alwaysRun = true)
    public void disconnect() throws Exception {
        client.stop();
        server.stop();
    }

    @Test
    public void serverSideConnectionRoutesMessagesToItsOwnCallback() throws Exception {
        RecordingCallback callback = new RecordingCallback();
        serverCallback.connectedClients.get(0).setCallback(callback);

        client.send(new Note());

        waitUntil(() -> callback.messages.size() == 1, "the connection callback receives the message");
        Assert.assertTrue(serverCallback.messageReceived.isEmpty(), "The server callback must be bypassed");
        Assert.assertEquals(server.getTotalMessages(), 1L, "Every message is counted, whichever callback handles it");
    }

    @Test
    public void serverSideConnectionRoutesDisconnectToItsOwnCallback() throws Exception {
        RecordingCallback callback = new RecordingCallback();
        Connection connection = serverCallback.connectedClients.get(0);
        connection.setCallback(callback);

        client.disconnect();

        waitUntil(() -> callback.disconnects.size() == 1, "the connection callback sees the disconnect");
        Assert.assertSame(callback.disconnects.get(0), connection);
        Assert.assertEquals(serverCallback.disconnects.get(), 0);
        waitUntil(() -> server.getConnections().isEmpty(), "the server stops tracking the connection");
    }

    @Test
    public void clientRoutesEventsToItsOwnCallback() throws Exception {
        RecordingCallback callback = new RecordingCallback();
        client.setCallback(callback);

        serverCallback.connectedClients.get(0).send(new Note());
        waitUntil(() -> callback.messages.size() == 1, "the connection callback receives the message");
        Assert.assertTrue(clientCallback.messageReceived.isEmpty(), "The client callback must be bypassed");

        client.send(new NotSerializable());
        waitUntil(() -> callback.errors.size() == 1, "the connection callback receives the error");
        Assert.assertFalse(clientCallback.errorReceived, "The client callback must be bypassed");

        client.disconnect();
        waitUntil(() -> callback.disconnects.size() == 1, "the connection callback sees the disconnect");
        Assert.assertFalse(clientCallback.disconnected, "The client callback must be bypassed");
    }

    @Test
    public void clearingTheCallbackRestoresTheOriginal() throws Exception {
        RecordingCallback callback = new RecordingCallback();
        Connection connection = serverCallback.connectedClients.get(0);
        connection.setCallback(callback);
        connection.setCallback(null);

        client.send(new Note());

        waitUntil(() -> serverCallback.messageReceived.size() == 1, "the server callback receives the message");
        Assert.assertTrue(callback.messages.isEmpty());
    }
}
