package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import org.testng.annotations.Test;

import java.time.Duration;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

/**
 * Each side must measure latency on its own, whatever heartbeat interval its peer uses.
 */
public class HeartbeatIntervalTest {

    @Test(timeOut = 20_000)
    public void serverMeasuresLatencyWhenClientHeartbeatsFaster() throws Exception {
        assertBothSidesMeasure(Duration.ofSeconds(2), Duration.ofSeconds(1));
    }

    @Test(timeOut = 20_000)
    public void clientMeasuresLatencyWhenServerHeartbeatsFaster() throws Exception {
        assertBothSidesMeasure(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private static void assertBothSidesMeasure(Duration serverInterval, Duration clientInterval) throws Exception {
        int port = freePort();
        BasicServer server = new BasicServer(port, new TestServerCallback());
        server.settings().setHeartbeatInterval(serverInterval);
        server.start();
        Client client = new Client("127.0.0.1", port, new TestClientCallback());
        client.settings().setHeartbeatInterval(clientInterval);
        try {
            client.connect();
            waitUntil(() -> server.getAverageLatencyMillis().isPresent(), "the server measures latency");
            waitUntil(() -> client.getLatencyMillis().isPresent(), "the client measures latency");
        } finally {
            client.stop();
            server.stop();
        }
    }
}
