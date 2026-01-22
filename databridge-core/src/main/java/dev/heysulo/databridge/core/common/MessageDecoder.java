package dev.heysulo.databridge.core.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {

    private final String filterPattern;

    public MessageDecoder(List<String> trustedPackages) {
        this.filterPattern = String.join(";", trustedPackages);
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }

        in.markReaderIndex();
        final int messageLength = in.readInt();
        if (in.readableBytes() < messageLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] byteArray = new byte[messageLength];
        in.readBytes(byteArray, 0, messageLength);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

        // Secure deserialization
        if (filterPattern != null && !filterPattern.isEmpty()) {
            ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(filterPattern + ";!*");
            objectInputStream.setObjectInputFilter(filter);
        }

        out.add(objectInputStream.readObject());
    }
}
