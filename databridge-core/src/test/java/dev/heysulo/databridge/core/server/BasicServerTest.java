package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.sendWithBackpressure;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

public class BasicServerTest {

    public static class TestMessage implements Message {
        public String message;

        public TestMessage(String message) {
            this.message = message;
        }
    }

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int MESSAGE_COUNT = 1000;

    private final List<Client> clients = new ArrayList<>();
    private EventLoopGroup eventLoops;
    private BasicServer server;
    private TestServerCallback serverCallback;
    private int serverPort;

    @BeforeClass
    void createEventLoops() {
        eventLoops = new NioEventLoopGroup(); // Avoids a graceful group shutdown after every test
    }

    @AfterClass(alwaysRun = true)
    void shutdownEventLoops() {
        eventLoops.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    }

    @BeforeMethod
    void startServer() throws Exception {
        serverPort = freePort();
        serverCallback = new TestServerCallback();
        server = new BasicServer(serverPort, serverCallback, eventLoops, eventLoops);
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    void stopServer() throws InterruptedException {
        for (Client client : clients) {
            client.stop();
        }
        clients.clear();
        server.stop();
    }

    private Client connectClient(TestClientCallback clientCallback) throws InterruptedException {
        Client client = new Client(SERVER_ADDRESS, serverPort, clientCallback, eventLoops);
        clients.add(client);
        client.connect();
        waitUntil(() -> serverCallback.connected && clientCallback.connected, "both sides report the connection");
        return client;
    }

    private void awaitDisconnection(TestClientCallback clientCallback) throws InterruptedException {
        waitUntil(() -> serverCallback.disconnected && clientCallback.disconnected, "both sides report the disconnection");
    }

    @Test
    void testCallbackEvents() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = connectClient(clientCallback);
        Assert.assertFalse(serverCallback.disconnected);
        Assert.assertFalse(clientCallback.disconnected);

        client.disconnect();
        awaitDisconnection(clientCallback);
    }

    @Test
    void testClientToServerCommunication() throws InterruptedException {
        Client client = connectClient(new TestClientCallback());

        TestMessage message = new TestMessage(UUID.randomUUID().toString());
        client.send(message);

        waitUntil(() -> serverCallback.messageReceived.size() == 1, "the server receives the message");
        Assert.assertEquals(message.message, ((TestMessage) serverCallback.messageReceived.get(0)).message);
    }

    @Test
    void testServerToClientCommunication() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        connectClient(clientCallback);

        TestMessage message = new TestMessage(UUID.randomUUID().toString());
        serverCallback.connectedClients.get(0).send(message);

        waitUntil(() -> clientCallback.messageReceived.size() == 1, "the client receives the message");
        Assert.assertEquals(message.message, ((TestMessage) clientCallback.messageReceived.get(0)).message);
    }

    @Test
    void testSend1000MessagesClientToServer() throws InterruptedException {
        Client client = connectClient(new TestClientCallback());

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            sendWithBackpressure(client, new TestMessage(UUID.randomUUID().toString()));
        }
        client.disconnect(); // Queued messages must still be delivered

        waitUntil(() -> serverCallback.messageReceived.size() == MESSAGE_COUNT, "the server receives every message");
    }

    @Test
    void testSend1000MessagesServerToClient() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        connectClient(clientCallback);

        Client serverSideClient = serverCallback.connectedClients.get(0);
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            sendWithBackpressure(serverSideClient, new TestMessage(UUID.randomUUID().toString()));
        }

        waitUntil(() -> clientCallback.messageReceived.size() == MESSAGE_COUNT, "the client receives every message");
    }

    @Test
    void testClientDisconnection() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = connectClient(clientCallback);

        client.disconnect();

        awaitDisconnection(clientCallback);
    }

    @Test
    void testRemoteClientDisconnection() throws InterruptedException {
        TestClientCallback clientCallback = new TestClientCallback();
        connectClient(clientCallback);

        serverCallback.connectedClients.get(0).disconnect();

        awaitDisconnection(clientCallback);
    }

    @Test
    void testLatencyMeasurement() throws InterruptedException {
        server.setHeartbeatInterval(1);
        Client client = new Client(SERVER_ADDRESS, serverPort, new TestClientCallback(), eventLoops);
        clients.add(client);
        client.setHeartbeatInterval(1);
        client.connect();

        waitUntil(() -> server.getLatency() >= 0, "the server measures latency");
        waitUntil(() -> client.getLatency() >= 0, "the client measures latency");
    }
}
