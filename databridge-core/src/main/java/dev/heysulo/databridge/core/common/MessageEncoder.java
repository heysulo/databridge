package dev.heysulo.databridge.core.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class MessageEncoder extends MessageToByteEncoder<Message> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws IOException {
        int startIndex = out.writerIndex();
        out.writeInt(0); // Reserve 4 bytes for length prefix

        ByteBufOutputStream bout = new ByteBufOutputStream(out);
        ObjectOutputStream objOutputStream = new ObjectOutputStream(bout);
        objOutputStream.writeObject(message);
        objOutputStream.flush();

        int bodyLength = out.writerIndex() - startIndex - 4;
        out.setInt(startIndex, bodyLength);
    }
}
