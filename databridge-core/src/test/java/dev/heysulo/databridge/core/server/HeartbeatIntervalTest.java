package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import org.testng.annotations.Test;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

/**
 * Each side must measure latency on its own, whatever heartbeat interval its peer uses.
 */
public class HeartbeatIntervalTest {

    @Test(timeOut = 20_000)
    public void serverMeasuresLatencyWhenClientHeartbeatsFaster() throws Exception {
        assertBothSidesMeasure(2, 1);
    }

    @Test(timeOut = 20_000)
    public void clientMeasuresLatencyWhenServerHeartbeatsFaster() throws Exception {
        assertBothSidesMeasure(1, 2);
    }

    private static void assertBothSidesMeasure(int serverInterval, int clientInterval) throws Exception {
        int port = freePort();
        BasicServer server = new BasicServer(port, new TestServerCallback());
        server.setHeartbeatInterval(serverInterval);
        server.start();
        Client client = new Client("127.0.0.1", port, new TestClientCallback());
        client.setHeartbeatInterval(clientInterval);
        try {
            client.connect();
            waitUntil(() -> server.getLatency() >= 0, "the server measures latency");
            waitUntil(() -> client.getLatency() >= 0, "the client measures latency");
        } finally {
            client.stop();
            server.stop();
        }
    }
}
