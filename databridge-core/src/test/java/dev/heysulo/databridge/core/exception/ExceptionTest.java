package dev.heysulo.databridge.core.exception;

import io.netty.channel.embedded.EmbeddedChannel;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExceptionTest {

    @Test
    public void testConnectionBufferedException() {
        ConnectionBufferedException ex = new ConnectionBufferedException();
        Assert.assertEquals(ex.getMessage(), "Connection buffered");
    }

    @Test
    public void testConnectionUnavailableException() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ConnectionUnavailableException ex = new ConnectionUnavailableException(channel);
        Assert.assertNotNull(ex.getMessage());
        Assert.assertTrue(ex.getMessage().contains("Id:"));
        Assert.assertTrue(ex.getMessage().contains("LocalAddress:"));
        Assert.assertTrue(ex.getMessage().contains("RemoteAddress:"));
    }
}
