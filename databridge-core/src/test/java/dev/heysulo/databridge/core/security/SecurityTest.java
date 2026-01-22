package dev.heysulo.databridge.core.security;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.BasicServer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InvalidClassException;
import java.util.UUID;

public class SecurityTest {

    private static final int SERVER_PORT = 6000;
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private BasicServer server;
    private Client client;

    // A message using a class that we will try to block
    public static class ForbiddenMessage implements Message {
        public String data = "Secret";
    }

    @Test
    public void testBlockedClass() throws Exception {
        TestServerCallback serverCallback = new TestServerCallback();
        server = new BasicServer(SERVER_PORT, serverCallback);

        // STRICT CONFIGURATION: Only allow our specific package, explicitly excluding
        // this inner class if needed,
        // but actually, since ForbiddenMessage is in
        // dev.heysulo.databridge.core.security, it matches dev.heysulo.**
        // So we need to restrict the server to ONLY allow something else, or use a
        // class from a different package.
        // Let's rely on the fact that java.awt.** is NOT in the default whitelist?
        // Default: dev.heysulo.**, java.util.**, java.lang.**
        // java.awt.Point should be BLOCKED by default.

        server.start();

        TestClientCallback clientCallback = new TestClientCallback();
        client = new Client(SERVER_ADDRESS, SERVER_PORT, clientCallback);
        client.connect();

        // We need to send a message that implements Message but contains a field of a
        // blocked type?
        // Or just the Message object itself?
        // The Decoder expects a Message.
        // If we send an object that IS a Message but its class is NOT whitelisted.
        // But we can't easily create a class outside the package in this test.

        // better approach: Clear the whitelist and only allow "java.lang.**"
        server.getTrustedPackages().clear();
        server.addTrustedPackage("java.lang.**");
        // Allow Handshake/Heartbeat messages
        server.addTrustedPackage("dev.heysulo.databridge.core.common.**");
        // Now other dev.heysulo.** packages (like core.security) are blocked.

        ClientMessage messageToSend = new ClientMessage(UUID.randomUUID().toString());

        try {
            client.send(messageToSend);
            // Give it time to arrive and fail
            Thread.sleep(500);

            // Server should NOT have received it
            Assert.assertEquals(serverCallback.messageReceived.size(), 0, "Message should have been blocked");

            // Check for exception in callback if we captured it?
            // The default TestServerCallback might just log errors.
            // The decoder throws Exception, Netty catches it and fires exceptionCaught.
            if (serverCallback.exceptionCaught != null) {
                serverCallback.exceptionCaught.printStackTrace();
            }
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

    public static class ClientMessage implements Message {
        public String data;

        public ClientMessage(String data) {
            this.data = data;
        }
    }
}
