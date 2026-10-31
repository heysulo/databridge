package dev.heysulo.databridge.core.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {

    public static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024; // 16 MB

    private final int maxFrameLength;
    private final ObjectInputFilter filter;

    public MessageDecoder(List<String> trustedPackages) {
        this(trustedPackages, DEFAULT_MAX_FRAME_LENGTH);
    }

    public MessageDecoder(List<String> trustedPackages, int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
        if (trustedPackages != null && !trustedPackages.isEmpty()) {
            this.filter = ObjectInputFilter.Config.createFilter(String.join(";", trustedPackages) + ";!*");
        } else {
            this.filter = ObjectInputFilter.Config.createFilter("!*");
        }
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }

        in.markReaderIndex();
        final int messageLength = in.readInt();
        if (messageLength <= 0 || messageLength > maxFrameLength) {
            throw new TooLongFrameException("Invalid frame length: " + messageLength + " (max: " + maxFrameLength + ")");
        }

        if (in.readableBytes() < messageLength) {
            in.resetReaderIndex();
            return;
        }

        ByteBuf slice = in.readRetainedSlice(messageLength);
        try (ByteBufInputStream byteBufInputStream = new ByteBufInputStream(slice);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteBufInputStream)) {

            if (filter != null) {
                objectInputStream.setObjectInputFilter(filter);
            }

            out.add(objectInputStream.readObject());
        } finally {
            slice.release();
        }
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }
}
