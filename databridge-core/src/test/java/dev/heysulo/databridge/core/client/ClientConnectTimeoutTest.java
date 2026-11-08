package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

public class ClientConnectTimeoutTest {

    // A reserved address that is never routed: connection attempts get no answer, as with a host that has gone
    private static final String UNANSWERED_ADDRESS = "10.255.255.1";

    @Test(timeOut = 10_000)
    public void connectGivesUpAfterTheConnectTimeout() throws Exception {
        Client client = new Client(UNANSWERED_ADDRESS, 9, new NoOpCallback());
        client.settings().setConnectTimeout(Duration.ofMillis(500));
        long start = System.nanoTime();
        try {
            Assert.expectThrows(Exception.class, client::connect);
        } finally {
            client.stop();
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        // Some networks refuse at once instead; either way it must not wait for Netty's 30 s default
        Assert.assertTrue(elapsedMillis < 5_000, "took " + elapsedMillis + " ms");
    }

    private static final class NoOpCallback implements ClientCallback {
        @Override
        public void onConnect(Client client) {
        }

        @Override
        public void onDisconnect(Client client) {
        }

        @Override
        public void onMessage(Client client, Message message) {
        }

        @Override
        public void onError(Client client, Throwable throwable) {
        }
    }
}
