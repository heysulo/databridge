package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ServerHandlerTest {

    @Test
    public void testHeartbeatSentOnScheduleEvenWhenRepliesKeepChannelBusy() {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        server.settings().setHeartbeatInterval(Duration.ofSeconds(1));
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));
        completeHandshake(channel);

        // A reply written just before the interval elapses must not postpone our own heartbeat
        channel.advanceTimeBy(900, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        channel.writeInbound(HeartbeatMessage.request());
        Assert.assertTrue(((HeartbeatMessage) channel.readOutbound()).isReply());

        channel.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        Object msg = channel.readOutbound();
        Assert.assertTrue(msg instanceof HeartbeatMessage);
        Assert.assertFalse(((HeartbeatMessage) msg).isReply());
    }

    @Test
    public void testNoHeartbeatBeforeHandshake() {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        server.settings().setHeartbeatInterval(Duration.ofSeconds(1));
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));

        channel.advanceTimeBy(5, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testHeartbeatsStopWhenChannelCloses() {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        server.settings().setHeartbeatInterval(Duration.ofSeconds(1));
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));
        completeHandshake(channel);

        channel.close();
        channel.advanceTimeBy(5, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testZeroIntervalDisablesHeartbeats() {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        server.settings().setHeartbeatInterval(Duration.ofSeconds(0));
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));
        completeHandshake(channel);

        channel.advanceTimeBy(5, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        Assert.assertTrue(channel.isActive());
        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testUserEventTriggered_ReaderIdleClosesChannel() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Simulate Reader IdleStateEvent
        handler.userEventTriggered(channel.pipeline().context(handler), IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        Assert.assertFalse(channel.isActive(), "Reader idle should close the channel to prevent zombie connections");
    }

    @Test
    public void testUserEventTriggered_OtherEvent() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        handler.userEventTriggered(channel.pipeline().context(handler), "SomeEvent");
    }

    @Test
    public void testChannelRead_HeartbeatReply() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        completeHandshake(channel);

        channel.writeInbound(HeartbeatMessage.replyTo(HeartbeatMessage.request()));

        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testChannelRead_HeartbeatRequest() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        completeHandshake(channel);

        HeartbeatMessage request = HeartbeatMessage.request();
        channel.writeInbound(request);

        // Should reply
        Object msg = channel.readOutbound();
        Assert.assertTrue(msg instanceof HeartbeatMessage);
        Assert.assertTrue(((HeartbeatMessage) msg).isReply());
        Assert.assertEquals(((HeartbeatMessage) msg).getTimestamp(), request.getTimestamp());
    }

    @Test
    public void testChannelRead_HandshakeSuccess() {
        TestServerCallback callback = new TestServerCallback();
        BasicServer server = new BasicServer(9000, callback);
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Before handshake, callback.connected should not be true
        Assert.assertFalse(callback.connected, "onConnect should not be called before handshake");

        HandshakeMessage handshake = new HandshakeMessage("client-1");
        channel.writeInbound(handshake);

        Object ack = channel.readOutbound();
        Assert.assertTrue(ack instanceof HandshakeAckMessage);
        Assert.assertTrue(((HandshakeAckMessage) ack).isSuccess());
        Assert.assertTrue(callback.connected, "onConnect should be called after successful handshake");
    }

    @Test
    public void testChannelRead_HandshakeWrongVersion() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        HandshakeMessage handshake = new HandshakeMessage("client");
        // Use reflection to set final field
        java.lang.reflect.Field versionField = HandshakeMessage.class.getDeclaredField("version");
        versionField.setAccessible(true);
        versionField.setInt(handshake, -1);

        channel.writeInbound(handshake);

        Assert.assertFalse(channel.isActive());
    }

    @Test
    public void testChannelRead_NotHandshaken() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound("Some random message");

        Assert.assertFalse(channel.isActive());
    }

    @Test
    public void testHeartbeatBeforeHandshakeClosesChannel() {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));

        channel.writeInbound(HeartbeatMessage.request());

        Assert.assertFalse(channel.isActive(), "Heartbeats must not keep an unauthenticated connection alive");
        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testHandshakeRegistersConnection() {
        TestServerCallback callback = new TestServerCallback();
        BasicServer server = new BasicServer(9000, callback);
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));

        completeHandshake(channel);

        Assert.assertEquals(server.getConnections().size(), 1);
        Assert.assertSame(callback.connectedClients.get(0), server.getConnections().iterator().next());

        channel.close();
        Assert.assertTrue(server.getConnections().isEmpty());
    }

    @Test
    public void testHeartbeatReplyRecordsLatencyOnTheConnection() {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        EmbeddedChannel channel = new EmbeddedChannel(new ServerInboundHandler(server));
        completeHandshake(channel);

        channel.writeInbound(HeartbeatMessage.replyTo(HeartbeatMessage.request()));

        Assert.assertTrue(server.getConnections().iterator().next().getLatencyMillis().isPresent());
    }

    private static void completeHandshake(EmbeddedChannel channel) {
        channel.writeInbound(new HandshakeMessage("client"));
        Assert.assertTrue(channel.readOutbound() instanceof HandshakeAckMessage);
    }
}
