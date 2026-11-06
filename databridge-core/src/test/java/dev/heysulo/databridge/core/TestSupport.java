package dev.heysulo.databridge.core;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.exception.ConnectionBufferedException;
import org.testng.Assert;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Shared helpers so that tests wait on conditions rather than fixed delays and never compete for a port.
 */
public final class TestSupport {

    private static final long TIMEOUT_MILLIS = 5_000;
    private static final long POLL_MILLIS = 5;

    private TestSupport() {
    }

    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Polls until the condition holds, failing the test with {@code description} if it never does.
     */
    public static void waitUntil(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        while (!condition.getAsBoolean()) {
            Assert.assertTrue(System.nanoTime() < deadline, "Timed out waiting until " + description);
            Thread.sleep(POLL_MILLIS);
        }
    }

    /**
     * Sends a message, waiting out backpressure in the way a real sender must.
     */
    public static void sendWithBackpressure(Client client, Message message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        while (true) {
            try {
                client.send(message);
                return;
            } catch (ConnectionBufferedException buffered) {
                Assert.assertTrue(System.nanoTime() < deadline, "Timed out waiting for the send buffer to drain");
                Thread.sleep(POLL_MILLIS);
            }
        }
    }
}
