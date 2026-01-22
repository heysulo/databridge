package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestServerCallback;
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
    public void testUserEventTriggered_OtherEvent() throws Exception {
        BasicServer server = new BasicServer(9000, new TestServerCallback());
        ServerInboundHandler handler = new ServerInboundHandler(server);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Simulate random object event (should update reference count or just pass
        // through if not handled)
        // Since super.userEventTriggered just fires it to next handler, checking no
        // exception is enough.
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

        // Server should have updated latency (we can't easily check internal volatile,
        // but no exception)
        // No outbound message
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

        // Send a string (or any other message) before handshake
        // Note: In real pipeline, Decoder converts, but Handler gets Object.
        // If we send a Heartbeat, it is handled separately.
        // We need to send something else.
        // The handler casts to (Message) eventually, so let's send a fake message but
        // verify it closes.
        // Actually the code checks `if (!handshaken)` and closes.

        // We can't send raw object because the handler expects (Message) eventually?
        // Ah, the first checks are Heartbeat and Handshake.
        // If it's neither, it falls through to:
        // if (!handshaken) { ctx.close(); return; }

        channel.writeInbound("Some random message"); // Not a Message instance, but code doesn't cast until later
        // Wait, line 59: server.getCallback().OnMessage(..., (Message) msg);
        // But the check `if (!handshaken)` is at line 51.
        // So it should close BEFORE casting.

        Assert.assertFalse(channel.isActive());
    }
}
