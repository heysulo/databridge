package dev.heysulo.databridge.core.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class MessageEncoder extends MessageToByteEncoder<Message> {

    private final int maxFrameLength;

    public MessageEncoder() {
        this(MessageDecoder.DEFAULT_MAX_FRAME_LENGTH);
    }

    public MessageEncoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void encode(ChannelHandlerContext context, Message message, ByteBuf out) throws IOException {
        int startIndex = out.writerIndex();
        out.writeInt(0); // Reserve 4 bytes for length prefix

        try (ObjectOutputStream objectOutput = new ObjectOutputStream(new ByteBufOutputStream(out))) {
            objectOutput.writeObject(message); // Closing the stream leaves the buffer open
        }

        int bodyLength = out.writerIndex() - startIndex - Integer.BYTES;
        if (bodyLength > maxFrameLength) {
            throw new EncoderException("Message of " + bodyLength + " bytes exceeds max frame " + maxFrameLength);
        }
        out.setInt(startIndex, bodyLength);
    }
}
