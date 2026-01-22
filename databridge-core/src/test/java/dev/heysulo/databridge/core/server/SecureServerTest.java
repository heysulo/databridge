package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.server.secure.SecureServer;
import dev.heysulo.databridge.core.ssl.BouncyCastleCertificateGenerator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.UUID;

public class SecureServerTest {

    private static final int SERVER_PORT = 4000;
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static SecureServer server;
    private static final TestServerCallback serverCallback = new TestServerCallback();

    @Test
    void testSecureServer() throws Exception {
        server = new SecureServer(SERVER_PORT, serverCallback, getServerSslContext());
        server.addTrustedPackage("dev.heysulo.**");
        server.addTrustedPackage("java.lang.**");
        server.start();

        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, SERVER_PORT, getClientSslContext(), clientCallback);
        client.connect();
        Thread.sleep(1000);

        // Send Message
        BasicServerTest.TestMessage message = new BasicServerTest.TestMessage(UUID.randomUUID().toString());
        client.send(message);
        Thread.sleep(100);
        Assert.assertEquals(serverCallback.messageReceived.size(), 1);
        Assert.assertEquals(message.message,
                ((BasicServerTest.TestMessage) serverCallback.messageReceived.get(0)).message);
        client.disconnect();
        server.stop();
    }

    public static SslContext getServerSslContext() throws Exception {
        KeyPair keyPair = BouncyCastleCertificateGenerator.generateKeyPair();
        X509Certificate certificate = BouncyCastleCertificateGenerator.generateCertificate(keyPair);
        return SslContextBuilder.forServer(keyPair.getPrivate(), certificate).build();
    }

    public static SslContext getClientSslContext() throws Exception {
        return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    }
}
