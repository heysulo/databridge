package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LifecycleAndMetricsTest {

    @Test
    public void testSharedEventLoopGroupNotTerminatedOnServerStop() throws Exception {
        int port = 7001;
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
        Thread.sleep(100);

        Assert.assertTrue(serverCallback.connected);

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
        int port = 7002;
        EventLoopGroup sharedWorker = new NioEventLoopGroup(1, new DefaultThreadFactory("shared-client-worker"));

        TestServerCallback serverCallback = new TestServerCallback();
        BasicServer server = new BasicServer(port, serverCallback);
        server.start();

        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client("127.0.0.1", port, clientCallback, sharedWorker);
        Assert.assertTrue(client.isSharedWorkerGroup());

        client.connect();
        Thread.sleep(100);

        Assert.assertTrue(clientCallback.connected);

        client.stop();

        // Shared group must NOT be shutting down
        Assert.assertFalse(sharedWorker.isShuttingDown(), "Shared client worker group must remain active");

        server.stop();
        sharedWorker.shutdownGracefully().sync();
    }

    @Test
    public void testMultiClientIndividualLatencyAndSessionTracking() throws Exception {
        int port = 7003;
        TestServerCallback serverCallback = new TestServerCallback();
        BasicServer server = new BasicServer(port, serverCallback);
        server.setHeartbeatInterval(1);
        server.start();

        TestClientCallback clientCallback1 = new TestClientCallback();
        Client client1 = new Client("127.0.0.1", port, clientCallback1);
        client1.setHeartbeatInterval(1);
        client1.connect();

        TestClientCallback clientCallback2 = new TestClientCallback();
        Client client2 = new Client("127.0.0.1", port, clientCallback2);
        client2.setHeartbeatInterval(1);
        client2.connect();

        // Wait for connection and heartbeats
        for (int i = 0; i < 50; i++) {
            if (server.getConnectedClients().size() == 2 && server.getAverageLatency() >= 0) {
                break;
            }
            Thread.sleep(100);
        }

        Assert.assertEquals(server.getConnectedClients().size(), 2, "Server should track 2 connected clients");

        // Each server-side client representation must have its own tracked latency
        for (Client serverSideClient : server.getConnectedClients()) {
            Assert.assertTrue(serverSideClient.getLatency() >= 0,
                    "Each client session must have its own measured latency");
        }

        Assert.assertTrue(server.getAverageLatency() >= 0, "Server average latency should be computed");
        Assert.assertTrue(client1.getLatency() >= 0, "Client 1 latency should be measured");
        Assert.assertTrue(client2.getLatency() >= 0, "Client 2 latency should be measured");

        // Disconnect first client
        client1.disconnect();
        Thread.sleep(200);
        Assert.assertEquals(server.getConnectedClients().size(), 1, "Connected clients count should decrease to 1");

        // Disconnect second client
        client2.disconnect();
        Thread.sleep(200);
        Assert.assertEquals(server.getConnectedClients().size(), 0, "Connected clients count should be 0");

        server.stop();
    }
}
