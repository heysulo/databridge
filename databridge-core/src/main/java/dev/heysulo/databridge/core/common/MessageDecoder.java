package dev.heysulo.databridge.core.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Decodes length-prefixed Java-serialized frames.
 *
 * <p>Every frame is deserialized through an {@link ObjectInputFilter} that enforces structural limits as well as
 * the class allowlist. A decoder created with {@code requireHandshake} additionally accepts only handshake classes
 * in small frames until {@link #completeHandshake()} is called, so an unauthenticated peer cannot reach the
 * general allowlist. Any decoding failure is terminal: the remaining input is discarded because the stream can no
 * longer be trusted, and the inbound handlers close the connection.
 */
public class MessageDecoder extends ByteToMessageDecoder {

    public static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024; // 16 MB
    public static final int HANDSHAKE_MAX_FRAME_LENGTH = 4 * 1024; // 4 KB
    public static final int MAX_OBJECT_DEPTH = 20;
    public static final int MAX_OBJECT_REFERENCES = 1_000_000;

    /**
     * Rules are evaluated in order and the first match wins, so exclusions must precede the allowances.
     */
    public static final List<String> DEFAULT_TRUSTED_PACKAGES = List.of(
            "!java.util.PriorityQueue",
            "!java.rmi.**",
            "!java.lang.reflect.**",
            "!java.lang.invoke.**",
            "dev.heysulo.**",
            "java.lang.*",
            "java.util.ArrayDeque",
            "java.util.ArrayList",
            "java.util.Arrays$ArrayList",
            "java.util.BitSet",
            "java.util.CollSer",
            "java.util.Collections$*",
            "java.util.Date",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.ImmutableCollections$*",
            "java.util.LinkedHashMap",
            "java.util.LinkedHashSet",
            "java.util.LinkedList",
            "java.util.Locale",
            "java.util.Map$Entry", // Checked by HashMap and HashSet while sizing their tables
            "java.util.TreeMap",
            "java.util.TreeSet",
            "java.util.UUID"
    );

    private static final ObjectInputFilter HANDSHAKE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=2;maxrefs=16;maxarray=0;maxbytes=" + HANDSHAKE_MAX_FRAME_LENGTH + ";"
                    + HandshakeMessage.class.getName() + ";"
                    + HandshakeAckMessage.class.getName() + ";!*");

    private final int maxFrameLength;
    private final ObjectInputFilter filter;
    private boolean awaitingHandshake;
    private boolean failed;

    public MessageDecoder(List<String> trustedPackages) {
        this(trustedPackages, DEFAULT_MAX_FRAME_LENGTH);
    }

    public MessageDecoder(List<String> trustedPackages, int maxFrameLength) {
        this(trustedPackages, maxFrameLength, false);
    }

    public MessageDecoder(List<String> trustedPackages, int maxFrameLength, boolean requireHandshake) {
        this.maxFrameLength = maxFrameLength;
        this.awaitingHandshake = requireHandshake;
        this.filter = createFilter(trustedPackages, maxFrameLength);
    }

    private static ObjectInputFilter createFilter(List<String> trustedPackages, int maxFrameLength) {
        String limits = "maxdepth=" + MAX_OBJECT_DEPTH
                + ";maxrefs=" + MAX_OBJECT_REFERENCES
                + ";maxbytes=" + maxFrameLength
                + ";maxarray=" + maxFrameLength + ";";
        String rules = trustedPackages == null || trustedPackages.isEmpty()
                ? ""
                : String.join(";", trustedPackages) + ";";
        return ObjectInputFilter.Config.createFilter(limits + rules + "!*");
    }

    /**
     * Switches from the handshake-only rules to the configured allowlist. Must be called on the channel's event
     * loop, which is guaranteed from an inbound handler: Netty delivers each decoded frame before decoding the next.
     */
    public void completeHandshake() {
        this.awaitingHandshake = false;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        if (failed) {
            in.skipBytes(in.readableBytes());
            return;
        }
        try {
            decodeFrame(in, out);
        } catch (Throwable cause) {
            failed = true;
            throw cause;
        }
    }

    private void decodeFrame(ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }

        int frameLimit = awaitingHandshake ? Math.min(HANDSHAKE_MAX_FRAME_LENGTH, maxFrameLength) : maxFrameLength;
        in.markReaderIndex();
        final int messageLength = in.readInt();
        if (messageLength <= 0 || messageLength > frameLimit) {
            throw new TooLongFrameException("Invalid frame length: " + messageLength + " (max: " + frameLimit + ")");
        }

        if (in.readableBytes() < messageLength) {
            in.resetReaderIndex();
            return;
        }

        ByteBuf slice = in.readRetainedSlice(messageLength);
        try (ByteBufInputStream byteBufInputStream = new ByteBufInputStream(slice);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteBufInputStream)) {
            objectInputStream.setObjectInputFilter(awaitingHandshake ? HANDSHAKE_FILTER : filter);
            out.add(objectInputStream.readObject());
        } finally {
            slice.release();
        }
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }
}
