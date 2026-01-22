package dev.heysulo.databridge.core.framing;

import dev.heysulo.databridge.core.common.MessageDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FramingTest {

    @Test
    public void testFragmentedPacket() throws Exception {
        List<String> trusted = new ArrayList<>();
        trusted.add("java.lang.**");
        trusted.add("dev.heysulo.**");

        EmbeddedChannel channel = new EmbeddedChannel(new MessageDecoder(trusted));

        String payload = "Hello World";
        byte[] serializedData = serialize(payload);
        int totalLength = serializedData.length;

        // 1. Write Length Prefix
        ByteBuf header = Unpooled.buffer(4);
        header.writeInt(totalLength);
        channel.writeInbound(header);

        // 2. Write FIRST HALF of data
        int half = totalLength / 2;
        ByteBuf p1 = Unpooled.wrappedBuffer(serializedData, 0, half);
        channel.writeInbound(p1);

        // decoder should NOT have produced an object yet
        Assert.assertTrue(channel.inboundMessages().isEmpty(), "Decoder yielded incomplete object");

        // 3. Write SECOND HALF
        ByteBuf p2 = Unpooled.wrappedBuffer(serializedData, half, totalLength - half);
        channel.writeInbound(p2);

        // decoder SHOULD have produced an object now
        Assert.assertFalse(channel.inboundMessages().isEmpty(), "Decoder failed to yield object");
        Object obj = channel.readInbound();
        Assert.assertEquals(obj, payload);
    }

    private byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }
}
