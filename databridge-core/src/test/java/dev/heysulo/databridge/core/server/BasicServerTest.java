package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import org.testng.Assert;
import org.testng.annotations.*;

import java.util.UUID;

public class BasicServerTest {

    public static class TestMessage implements Message {
        public String message;

        public TestMessage(String message) {
            this.message = message;
        }
    }

    private static int SERVER_PORT = 3000;
    private static String SERVER_ADDRESS = "127.0.0.1";
    private static BasicServer server;
    private static TestServerCallback serverCallback = new TestServerCallback();

    @BeforeClass
    void serverSetup() throws InterruptedException {
        server = new BasicServer(SERVER_PORT, serverCallback);
        server.start();
    }

    @BeforeMethod
    void resetCallbacks() {
        serverCallback.reset();
    }

    @AfterTest
    void serverShutdown() throws InterruptedException {
        server.stop();
    }

    @Test
    void testCallbackEvents() throws InterruptedException {
        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);

        // OnConnect
        client.connect();
        Thread.sleep(100);
        Assert.assertTrue(serverCallback.connected);
        Assert.assertTrue(clientCallback.connected);
        Assert.assertFalse(serverCallback.disconnected);
        Assert.assertFalse(clientCallback.disconnected);

        // OnDisconnect
        client.disconnect();
        Thread.sleep(100);
        Assert.assertTrue(serverCallback.disconnected);
        Assert.assertTrue(clientCallback.disconnected);
    }

    @Test
    void testClientToServerCommunication() throws InterruptedException {
        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();

        // Client to Server Message
        TestMessage message = new TestMessage(UUID.randomUUID().toString());
        client.send(message);
        Thread.sleep(100);
        Assert.assertEquals(serverCallback.messageReceived.size(), 1);
        Assert.assertEquals(message.message, ((TestMessage) serverCallback.messageReceived.get(0)).message);
        client.disconnect();
    }

    @Test
    void testServerToClientCommunication() throws InterruptedException {
        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();
        Thread.sleep(100);

        // Client to Server Message
        TestMessage message = new TestMessage(UUID.randomUUID().toString());
        Client serverSideClient = serverCallback.connectedClients.get(0);
        serverSideClient.send(message);
        Thread.sleep(100);
        Assert.assertEquals(clientCallback.messageReceived.size(), 1);
        Assert.assertEquals(message.message, ((TestMessage) clientCallback.messageReceived.get(0)).message);
        client.disconnect();
    }

    @Test
    void testSend1000MessagesClientToServer() throws InterruptedException {
        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();
        Thread.sleep(100);

        // Client to Server Message
        for (int i = 0; i < 1000; i++) {
            TestMessage message = new TestMessage(UUID.randomUUID().toString());
            client.send(message);
            // Thread.sleep(1); // Should be okay in 2 machines
        }
        client.disconnect();
        Thread.sleep(1000);
        Assert.assertEquals(serverCallback.messageReceived.size(), 1000);
    }

    @Test
    void testSend1000MessagesServerToClient() throws InterruptedException {
        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();
        Thread.sleep(100);

        // Client to Server Message
        Client serverSideClient = serverCallback.connectedClients.get(0);
        for (int i = 0; i < 1000; i++) {
            TestMessage message = new TestMessage(UUID.randomUUID().toString());
            serverSideClient.send(message);
            Thread.sleep(1); // Should be okay in 2 machines
        }
        client.disconnect();
        Thread.sleep(1000);
        Assert.assertEquals(clientCallback.messageReceived.size(), 1000);
    }

    @Test
    void testClientDisconnection() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();
        Thread.sleep(100);

        Assert.assertTrue(serverCallback.connected);
        Assert.assertTrue(clientCallback.connected);
        client.disconnect();
        Thread.sleep(100);
        Assert.assertTrue(serverCallback.disconnected);
        Assert.assertTrue(clientCallback.disconnected);
    }

    @Test
    void testRemoteClientDisconnection() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();
        Thread.sleep(100);

        Assert.assertTrue(serverCallback.connected);
        Assert.assertTrue(clientCallback.connected);
        Client remoteClient = serverCallback.connectedClients.get(0);
        remoteClient.disconnect();
        Thread.sleep(100);
        Assert.assertTrue(serverCallback.disconnected);
        Assert.assertTrue(clientCallback.disconnected);
    }

    @Test
    void testLatencyMeasurement() throws InterruptedException {
        // Client Setup
        server.setHeartbeatInterval(1); // Fast heartbeats for testing
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.setHeartbeatInterval(1);
        client.connect();

        // Wait for at least one heartbeat cycle (ping + pong)
        // Interval is 1s, so wait ~3s to be sure
        Thread.sleep(3500);

        long serverLatency = server.getLatency();
        long clientLatency = client.getLatency();

        System.out.println("Server Latency: " + serverLatency + "ms");
        System.out.println("Client Latency: " + clientLatency + "ms");

        Assert.assertTrue(serverLatency >= 0, "Server latency should be measured");
        Assert.assertTrue(clientLatency >= 0, "Client latency should be measured");

        client.disconnect();
    }
}