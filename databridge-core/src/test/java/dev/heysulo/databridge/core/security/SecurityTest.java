package dev.heysulo.databridge.core.security;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.BasicServer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InvalidClassException;
import java.util.PriorityQueue;
import java.util.UUID;

public class SecurityTest {

    private static final int SERVER_PORT = 6000;
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private BasicServer server;
    private Client client;

    @Test
    public void testBlockedClass() throws Exception {
        TestServerCallback serverCallback = new TestServerCallback();
        server = new BasicServer(SERVER_PORT, serverCallback);

        // Clear whitelist and only allow java.lang and core.common (for handshake/heartbeat)
        server.getTrustedPackages().clear();
        server.addTrustedPackage("java.lang.*");
        server.addTrustedPackage("dev.heysulo.databridge.core.common.**");

        server.start();

        TestClientCallback clientCallback = new TestClientCallback();
        client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();

        // ClientMessage is in dev.heysulo.databridge.core.security, so it must be blocked
        ClientMessage messageToSend = new ClientMessage(UUID.randomUUID().toString());

        try {
            client.send(messageToSend);
            Thread.sleep(500);

            // Server should NOT have received it
            Assert.assertEquals(serverCallback.messageReceived.size(), 0, "Message should have been blocked");

            Throwable e = serverCallback.exceptionCaught;
            boolean found = false;
            while (e != null) {
                if (e instanceof InvalidClassException) {
                    found = true;
                    break;
                }
                e = e.getCause();
            }
            Assert.assertTrue(found, "Should be InvalidClassException but was " + serverCallback.exceptionCaught);

        } finally {
            client.disconnect();
            server.stop();
        }
    }

    @Test
    public void testPriorityQueueBlockedByDefault() throws Exception {
        int port = 6001;
        TestServerCallback serverCallback = new TestServerCallback();
        BasicServer testServer = new BasicServer(port, serverCallback);
        testServer.start();

        TestClientCallback clientCallback = new TestClientCallback();
        Client testClient = new Client(SERVER_ADDRESS, port, clientCallback);
        testClient.connect();

        MessageWithPriorityQueue message = new MessageWithPriorityQueue();

        try {
            testClient.send(message);
            Thread.sleep(500);

            Assert.assertEquals(serverCallback.messageReceived.size(), 0, "PriorityQueue message should be blocked by default");

            Throwable e = serverCallback.exceptionCaught;
            boolean found = false;
            while (e != null) {
                if (e instanceof InvalidClassException) {
                    found = true;
                    break;
                }
                e = e.getCause();
            }
            Assert.assertTrue(found, "Should be InvalidClassException for PriorityQueue gadget block");

        } finally {
            testClient.disconnect();
            testServer.stop();
        }
    }

    public static class ClientMessage implements Message {
        public String data;

        public ClientMessage(String data) {
            this.data = data;
        }
    }

    public static class MessageWithPriorityQueue implements Message {
        public PriorityQueue<String> queue = new PriorityQueue<>();

        public MessageWithPriorityQueue() {
            queue.add("item");
        }
    }
}
