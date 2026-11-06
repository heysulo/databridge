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

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

public class SecureServerTest {

    private static final String SERVER_ADDRESS = "127.0.0.1";

    @Test
    void testSecureServer() throws Exception {
        int port = freePort();
        TestServerCallback serverCallback = new TestServerCallback();
        SecureServer server = new SecureServer(port, serverCallback, getServerSslContext());
        server.addTrustedPackage("dev.heysulo.**");
        server.addTrustedPackage("java.lang.**");
        server.start();

        // Client Setup
        TestClientCallback clientCallback = new TestClientCallback();
        Client client = new Client(SERVER_ADDRESS, port, getClientSslContext(), clientCallback);
        client.connect();
        waitUntil(() -> clientCallback.connected, "the TLS client reports the connection");

        // Send Message
        BasicServerTest.TestMessage message = new BasicServerTest.TestMessage(UUID.randomUUID().toString());
        client.send(message);
        waitUntil(() -> serverCallback.messageReceived.size() == 1, "the server receives the message");
        Assert.assertEquals(message.message,
                ((BasicServerTest.TestMessage) serverCallback.messageReceived.get(0)).message);
        client.disconnect();
        server.stop();
    }

    @Test
    void testHostnameVerification() throws Exception {
        KeyPair keyPair = BouncyCastleCertificateGenerator.generateKeyPair();
        X509Certificate certificate = BouncyCastleCertificateGenerator.generateCertificate(keyPair);
        SslContext serverContext = SslContextBuilder.forServer(keyPair.getPrivate(), certificate).build();
        SslContext clientContext = SslContextBuilder.forClient().trustManager(certificate).build();
        int port = freePort();
        SecureServer secureServer = new SecureServer(port, new TestServerCallback(), serverContext);
        secureServer.start();
        try {
            Assert.assertTrue(completesHandshake("localhost", port, clientContext, true),
                    "A certificate for localhost must be accepted for localhost");
            Assert.assertFalse(completesHandshake("127.0.0.1", port, clientContext, true),
                    "A certificate for localhost must be rejected for another address");
            Assert.assertTrue(completesHandshake("127.0.0.1", port, clientContext, false),
                    "Verification must be possible to opt out of");
        } finally {
            secureServer.stop();
        }
    }

    private static boolean completesHandshake(String host, int port, SslContext context, boolean verifyHostname)
            throws Exception {
        TestClientCallback callback = new TestClientCallback();
        Client client = new Client(host, port, context, callback);
        client.setHostnameVerificationEnabled(verifyHostname);
        try {
            client.connect();
            waitUntil(() -> callback.connected || callback.errorReceived, "the TLS handshake finishes");
            return callback.connected;
        } finally {
            client.stop();
        }
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
