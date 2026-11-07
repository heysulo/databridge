package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

public class LifecycleAndMetricsTest {

    @Test
    public void testSharedEventLoopGroupNotTerminatedOnServerStop() throws Exception {
        int port = freePort();
        EventLoopGroup sharedBoss = new NioEventLoopGroup(1, new DefaultThreadFactory("shared-boss"));
        EventLoopGroup sharedWorker = new NioEventLoopGroup(1, new DefaultThreadFactory("shared-worker"));

        TestServerCallback serverCallback = new TestServerCallback();
        BasicServer server = new BasicServer(port, serverCallback, sharedBoss, sharedWorker);
        Assert.assertTrue(server.isSharedBossGroup());
        Assert.assertTrue(server.isSharedWorkerGroup());

        server.start();

        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client("127.0.0.1", port, clientCallback);
        client.connect();
        waitUntil(() -> serverCallback.connected, "the server reports the connection");

        client.disconnect();
        server.stop();

        // Shared groups must NOT be shutting down or shut down
        Assert.assertFalse(sharedBoss.isShuttingDown(), "Shared boss group must remain active");
        Assert.assertFalse(sharedWorker.isShuttingDown(), "Shared worker group must remain active");

        // Clean up shared groups
        sharedBoss.shutdownGracefully().sync();
        sharedWorker.shutdownGracefully().sync();
    }

    @Test
    public void testSharedWorkerGroupNotTerminatedOnClientStop() throws Exception {
        int port = freePort();
        EventLoopGroup sharedWorker = new NioEventLoopGroup(1, new DefaultThreadFactory("shared-client-worker"));

        TestServerCallback serverCallback = new TestServerCallback();
        BasicServer server = new BasicServer(port, serverCallback);
        server.start();

        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client("127.0.0.1", port, clientCallback, sharedWorker);
        Assert.assertTrue(client.isSharedWorkerGroup());

        client.connect();
        waitUntil(() -> clientCallback.connected, "the client reports the connection");

        client.stop();

        // Shared group must NOT be shutting down
        Assert.assertFalse(sharedWorker.isShuttingDown(), "Shared client worker group must remain active");

        server.stop();
        sharedWorker.shutdownGracefully().sync();
    }

    @Test
    public void testMultiClientIndividualLatencyAndSessionTracking() throws Exception {
        int port = freePort();
        TestServerCallback serverCallback = new TestServerCallback();
        BasicServer server = new BasicServer(port, serverCallback);
        server.settings().setHeartbeatInterval(Duration.ofSeconds(1));
        server.start();

        TestClientCallback clientCallback1 = new TestClientCallback();
        Client client1 = new Client("127.0.0.1", port, clientCallback1);
        client1.settings().setHeartbeatInterval(Duration.ofSeconds(1));
        client1.connect();

        TestClientCallback clientCallback2 = new TestClientCallback();
        Client client2 = new Client("127.0.0.1", port, clientCallback2);
        client2.settings().setHeartbeatInterval(Duration.ofSeconds(1));
        client2.connect();

        waitUntil(() -> server.getConnections().size() == 2, "the server tracks 2 connected clients");

        // Each server-side client representation must have its own tracked latency
        waitUntil(() -> server.getConnections().stream().allMatch(client -> client.getLatencyMillis().isPresent()),
                "each client session has its own measured latency");
        Assert.assertTrue(server.getAverageLatencyMillis().isPresent(), "Server average latency should be computed");

        waitUntil(() -> client1.getLatencyMillis().isPresent(), "client 1 measures latency");
        waitUntil(() -> client2.getLatencyMillis().isPresent(), "client 2 measures latency");

        // Disconnect first client
        client1.disconnect();
        waitUntil(() -> server.getConnections().size() == 1, "the connected client count decreases to 1");

        // Disconnect second client
        client2.disconnect();
        waitUntil(() -> server.getConnections().isEmpty(), "the connected client count decreases to 0");

        server.stop();
    }
}
