package dev.heysulo.databridge.core.client;

import dev.heysulo.databridge.core.common.ChannelConnection;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.ProtocolHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

final class ClientInboundHandler extends ProtocolHandler {

    private static final String CLIENT_ID_PREFIX = "Client-";

    private final Client client;

    ClientInboundHandler(Client client) {
        super(client.settings(), HandshakeAckMessage.class);
        this.client = client;
    }

    @Override
    protected ChannelConnection connection() {
        return client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ctx.writeAndFlush(new HandshakeMessage(CLIENT_ID_PREFIX + UUID.randomUUID()));
    }

    @Override
    protected void onHandshake(ChannelHandlerContext ctx, Message handshake) {
        HandshakeAckMessage ack = (HandshakeAckMessage) handshake;
        if (ack.isSuccess()) {
            establish(ctx);
            client.getCallback().onConnect(client);
        } else {
            rejectProtocolViolation(ctx, "Handshake rejected: " + ack.getMessage());
        }
    }

    @Override
    protected void onMessage(Message message) {
        client.getCallback().onMessage(client, message);
    }

    @Override
    protected void onError(Throwable cause) {
        client.getCallback().onError(client, cause);
    }

    @Override
    protected void onDisconnected() {
        client.getCallback().onDisconnect(client);
    }
}
