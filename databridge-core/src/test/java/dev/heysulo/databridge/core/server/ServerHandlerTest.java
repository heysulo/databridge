package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ServerHandlerTest {

    @Test
    public void testUserEventTriggered_IdleState() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Simulate IdleStateEvent
        handler.userEventTriggered(channel.pipeline().context(handler), IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);

        // Check for HeartbeatMessage
        Object msg = channel.readOutbound();
        Assert.assertTrue(msg instanceof HeartbeatMessage);
        Assert.assertFalse(((HeartbeatMessage) msg).isReply);
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

        long now = System.nanoTime();
        HeartbeatMessage hb = new HeartbeatMessage(now, true);
        channel.writeInbound(hb);

        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testChannelRead_HeartbeatRequest() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        long now = System.nanoTime();
        HeartbeatMessage hb = new HeartbeatMessage(now, false);
        channel.writeInbound(hb);

        // Should reply
        Object msg = channel.readOutbound();
        Assert.assertTrue(msg instanceof HeartbeatMessage);
        Assert.assertTrue(((HeartbeatMessage) msg).isReply);
        Assert.assertEquals(((HeartbeatMessage) msg).timestamp, now);
    }

    @Test
    public void testChannelRead_HandshakeSuccess() {
        TestServerCallback callback = new TestServerCallback();
        BasicServer server = new BasicServer(9000, callback);
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Before handshake, callback.connected should not be true
        Assert.assertFalse(callback.connected, "OnConnect should not be called before handshake");

        HandshakeMessage handshake = new HandshakeMessage("client-1");
        channel.writeInbound(handshake);

        Object ack = channel.readOutbound();
        Assert.assertTrue(ack instanceof HandshakeAckMessage);
        Assert.assertTrue(((HandshakeAckMessage) ack).success);
        Assert.assertTrue(callback.connected, "OnConnect should be called after successful handshake");
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
}
