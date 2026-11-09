package dev.heysulo.databridge.core.server;

import dev.heysulo.databridge.core.callback.TestClientCallback;
import dev.heysulo.databridge.core.callback.TestServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.server.secure.SecureServer;
import dev.heysulo.databridge.core.ssl.BouncyCastleCertificateGenerator;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

public class ReloadableTlsTest {

    private static final String HOST = "127.0.0.1";

    private record Identity(KeyPair keys, X509Certificate certificate) {
        static Identity create() throws Exception {
            KeyPair keys = BouncyCastleCertificateGenerator.generateKeyPair();
            return new Identity(keys, BouncyCastleCertificateGenerator.generateCertificate(keys));
        }

        SslContext server(ClientAuth clientAuth) throws Exception {
            return SslContextBuilder.forServer(keys.getPrivate(), certificate)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).clientAuth(clientAuth).build();
        }

        SslContext client() throws Exception {
            return SslContextBuilder.forClient().keyManager(keys.getPrivate(), certificate)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        }
    }

    private static SslContext anonymousClient() throws Exception {
        return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    }

    private static Client connect(int port, SslContext context, TestClientCallback callback) throws Exception {
        Client client = new Client(HOST, port, context, callback);
        client.setHostnameVerificationEnabled(false);
        client.connect();
        waitUntil(() -> callback.connected || callback.errorReceived, "the client connects");
        return client;
    }

    @Test
    void newConnectionsUseTheLatestContext() throws Exception {
        Identity first = Identity.create();
        Identity second = Identity.create();
        AtomicReference<SslContext> current = new AtomicReference<>(first.server(ClientAuth.NONE));
        int port = freePort();
        SecureServer server = new SecureServer(port, new TestServerCallback(), current::get, false, null, null);
        server.start();
        try {
            TestClientCallback before = new TestClientCallback();
            Client old = connect(port, anonymousClient(), before);
            Assert.assertEquals(old.getPeerCertificates().get(0), first.certificate());

            current.set(second.server(ClientAuth.NONE));
            Client renewed = connect(port, anonymousClient(), new TestClientCallback());
            Assert.assertEquals(renewed.getPeerCertificates().get(0), second.certificate(), "the renewed one");
            Assert.assertTrue(old.isConnected(), "existing connections are kept");
            Assert.assertEquals(old.getPeerCertificates().get(0), first.certificate());
            old.stop();
            renewed.stop();
        } finally {
            server.stop();
        }
    }

    @Test
    void exposesTheClientsCertificate() throws Exception {
        Identity serverIdentity = Identity.create();
        Identity clientIdentity = Identity.create();
        TestServerCallback serverCallback = new TestServerCallback();
        int port = freePort();
        SecureServer server = new SecureServer(port, serverCallback, serverIdentity.server(ClientAuth.OPTIONAL));
        server.start();
        try {
            Client client = connect(port, clientIdentity.client(), new TestClientCallback());
            waitUntil(() -> serverCallback.connectedClients.size() == 1, "the server sees the client");
            Connection remote = serverCallback.connectedClients.get(0);
            waitUntil(remote::isSecure, "the handshake completes on the server");
            Assert.assertEquals(remote.getPeerCertificates().get(0), clientIdentity.certificate());
            client.stop();

            // A client without a certificate is still admitted when it is optional, and presents none
            serverCallback.connectedClients.clear();
            Client anonymous = connect(port, anonymousClient(), new TestClientCallback());
            waitUntil(() -> serverCallback.connectedClients.size() == 1, "the server sees the second client");
            Connection second = serverCallback.connectedClients.get(0);
            waitUntil(second::isSecure, "the second handshake completes");
            Assert.assertTrue(second.getPeerCertificates().isEmpty());
            anonymous.stop();
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsPlainAndTlsClientsOnOnePortWhenAllowed() throws Exception {
        Identity serverIdentity = Identity.create();
        TestServerCallback serverCallback = new TestServerCallback();
        int port = freePort();
        SslContext context = serverIdentity.server(ClientAuth.NONE);
        SecureServer server = new SecureServer(port, serverCallback, () -> context, true, null, null);
        server.settings().addTrustedPackage("dev.heysulo.**");
        server.settings().addTrustedPackage("java.lang.**");
        server.start();
        try {
            Client secure = connect(port, anonymousClient(), new TestClientCallback());
            secure.send(new BasicServerTest.TestMessage("over TLS"));
            TestClientCallback plainCallback = new TestClientCallback();
            Client plain = connect(port, null, plainCallback);
            Assert.assertTrue(plainCallback.connected, "a plain client is admitted");
            plain.send(new BasicServerTest.TestMessage("in the clear"));
            waitUntil(() -> serverCallback.messageReceived.size() == 2, "both messages arrive");
            Assert.assertTrue(secure.isSecure());
            Assert.assertFalse(plain.isSecure());
            Assert.assertTrue(plain.getPeerCertificates().isEmpty());
            secure.stop();
            plain.stop();
        } finally {
            server.stop();
        }
    }

    @Test
    void refusesPlainClientsUnlessAllowed() throws Exception {
        int port = freePort();
        TestServerCallback serverCallback = new TestServerCallback();
        SecureServer server = new SecureServer(port, serverCallback, Identity.create().server(ClientAuth.NONE));
        server.start();
        try {
            TestClientCallback callback = new TestClientCallback();
            Client plain = new Client(HOST, port, callback);
            plain.connect();
            // The server drops it on its first bytes, before the protocol handshake can complete
            waitUntil(() -> !plain.isConnected(), "the plain client is turned away");
            Assert.assertFalse(callback.connected, "the protocol handshake never completes");
            plain.stop();
        } finally {
            server.stop();
        }
    }
}
