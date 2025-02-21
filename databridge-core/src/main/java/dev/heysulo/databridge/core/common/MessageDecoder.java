package dev.heysulo.databridge.core.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }

        in.markReaderIndex();
        final int messageLength = in.readInt();
        if (in.readableBytes() + Integer.BYTES < messageLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] byteArray = new byte[messageLength];
        in.readBytes(byteArray, 0, messageLength);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        out.add(objectInputStream.readObject());
    }
}
