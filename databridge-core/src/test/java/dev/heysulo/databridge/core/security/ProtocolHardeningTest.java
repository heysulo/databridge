package dev.heysulo.databridge.core.security;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.HandshakeAckMessage;
import dev.heysulo.databridge.core.common.HandshakeMessage;
import dev.heysulo.databridge.core.common.HeartbeatMessage;
import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.common.MessageDecoder;
import dev.heysulo.databridge.core.server.BasicServer;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.DecoderException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.heysulo.databridge.core.TestSupport.freePort;
import static dev.heysulo.databridge.core.TestSupport.waitUntil;

/**
 * Exercises the connection-level defenses using raw sockets, which can break the protocol in ways a Client never does.
 */
public class ProtocolHardeningTest {

    private static final long WAIT_MILLIS = 5_000;

    private final RecordingServerCallback serverCallback = new RecordingServerCallback();
    private final List<Client> clients = new ArrayList<>();
    private EventLoopGroup sharedGroup;
    private BasicServer server;
    private int port;

    public static class TextMessage implements Message {
        public final String text;

        public TextMessage(String text) {
            this.text = text;
        }
    }

    public static class UnserializableMessage implements Message {
        public final Object notSerializable = new Object();
    }

    public static class LargeMessage implements Message {
        public final byte[] payload = new byte[4 * 1024];
    }

    private static class RecordingServerCallback implements ServerCallback {
        final AtomicInteger connects = new AtomicInteger();
        final AtomicInteger messages = new AtomicInteger();
        final Set<Throwable> errors = ConcurrentHashMap.newKeySet();

        @Override
        public void onConnect(Server server, Connection client) {
            connects.incrementAndGet();
        }

        @Override
        public void onDisconnect(Server server, Connection client) {
        }

        @Override
        public void onMessage(Server server, Connection client, Message msg) {
            messages.incrementAndGet();
        }

        @Override
        public void onError(Server server, Connection client, Throwable cause) {
            errors.add(cause);
        }
    }

    private static class RecordingClientCallback implements ClientCallback {
        final AtomicInteger connects = new AtomicInteger();
        final Set<Throwable> errors = ConcurrentHashMap.newKeySet();

        @Override
        public void onConnect(Client client) {
            connects.incrementAndGet();
        }

        @Override
        public void onDisconnect(Client client) {
        }

        @Override
        public void onMessage(Client client, Message msg) {
        }

        @Override
        public void onError(Client client, Throwable cause) {
            errors.add(cause);
        }
    }

    @BeforeClass
    public void createEventLoops() {
        sharedGroup = new NioEventLoopGroup(); // Avoids a graceful group shutdown after every test
    }

    @AfterClass(alwaysRun = true)
    public void shutdownEventLoops() {
        sharedGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    }

    @BeforeMethod
    public void startServer() throws Exception {
        port = freePort();
        server = new BasicServer(port, serverCallback, sharedGroup, sharedGroup);
        server.settings().addTrustedPackage(ProtocolHardeningTest.class.getName() + "*");
        serverCallback.connects.set(0);
        serverCallback.messages.set(0);
        serverCallback.errors.clear();
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void stopServer() throws Exception {
        for (Client client : clients) {
            client.stop();
        }
        clients.clear();
        server.stop();
    }

    @Test(timeOut = 20_000)
    public void hugeArrayHeaderIsRejectedBeforeAllocation() throws Exception {
        byte[] body = serialize(new long[0]);
        ByteBuffer.wrap(body).putInt(body.length - Integer.BYTES, 0x7FFFFF00);

        try (Socket socket = connect()) {
            write(socket, frame(body));
            assertClosedByServer(socket);
        }
        Assert.assertTrue(serverCallback.errors.stream().noneMatch(OutOfMemoryError.class::isInstance));
    }

    @Test(timeOut = 20_000)
    public void nestedSetBombIsRejectedBeforeHandshake() throws Exception {
        try (Socket socket = connect()) {
            write(socket, frame(serialize(nestedSets(40))));
            assertClosedByServer(socket);
        }
        connectClient();
    }

    @Test(timeOut = 20_000)
    public void nestedSetBombIsRejectedAfterHandshake() throws Exception {
        try (Socket socket = connect()) {
            handshake(socket);
            write(socket, frame(serialize(nestedSets(40))));
            assertClosedByServer(socket);
        }
        connectClient();
    }

    @Test(timeOut = 20_000)
    public void deepestPermittedNestingDecodesQuickly() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageDecoder(MessageDecoder.DEFAULT_TRUSTED_PACKAGES));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(frame(serialize(nestedSets(MessageDecoder.MAX_OBJECT_DEPTH)))));
        } catch (DecoderException expected) {
            // Rejection is also acceptable: the point is that this returns quickly
        }
        Assert.assertTrue(System.nanoTime() < deadline, "Permitted depth must not stall the event loop");
        channel.finishAndReleaseAll();
    }

    @Test(timeOut = 20_000)
    public void oversizedFrameIsRejectedBeforeHandshake() throws Exception {
        try (Socket socket = connect()) {
            write(socket, ByteBuffer.allocate(Integer.BYTES).putInt(MessageDecoder.HANDSHAKE_MAX_FRAME_LENGTH + 1).array());
            assertClosedByServer(socket);
        }
    }

    @Test(timeOut = 20_000)
    public void applicationMessageIsRejectedBeforeHandshake() throws Exception {
        try (Socket socket = connect()) {
            write(socket, frame(serialize(new TextMessage("too early"))));
            assertClosedByServer(socket);
        }
        Assert.assertEquals(serverCallback.messages.get(), 0);
    }

    @Test(timeOut = 20_000)
    public void heartbeatsCannotReplaceTheHandshake() throws Exception {
        try (Socket socket = connect()) {
            write(socket, frame(serialize(HeartbeatMessage.request())));
            assertClosedByServer(socket);
        }
        Assert.assertEquals(serverCallback.connects.get(), 0);
    }

    @Test(timeOut = 20_000)
    public void silentConnectionIsClosedAtHandshakeTimeout() throws Exception {
        server.setHandshakeTimeout(Duration.ofSeconds(1));
        try (Socket socket = connect()) {
            assertClosedByServer(socket);
        }
    }

    @Test(timeOut = 20_000)
    public void partialFrameDoesNotExtendHandshakeTimeout() throws Exception {
        server.setHandshakeTimeout(Duration.ofSeconds(1));
        try (Socket socket = connect()) {
            write(socket, new byte[]{0});
            assertClosedByServer(socket);
        }
    }

    @Test(timeOut = 20_000)
    public void completedHandshakeOutlivesHandshakeTimeout() throws Exception {
        server.setHandshakeTimeout(Duration.ofSeconds(1));
        try (Socket socket = connect()) {
            handshake(socket);
            socket.setSoTimeout(1_500);
            Assert.assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read(new byte[1]));
        }
    }

    @Test(timeOut = 20_000)
    public void invalidFrameLengthClosesEstablishedConnection() throws Exception {
        try (Socket socket = connect()) {
            handshake(socket);
            write(socket, ByteBuffer.allocate(Integer.BYTES).putInt(-5).array());
            assertClosedByServer(socket);
        }
        waitUntil(() -> serverCallback.errors.stream().anyMatch(DecoderException.class::isInstance), "the error is reported");
    }

    @Test(timeOut = 20_000)
    public void framesAfterAnInvalidFrameAreNotDelivered() throws Exception {
        try (Socket socket = connect()) {
            handshake(socket);
            ByteArrayOutputStream burst = new ByteArrayOutputStream();
            burst.write(ByteBuffer.allocate(Integer.BYTES).putInt(-5).array());
            burst.write(frame(serialize(new TextMessage("after corruption"))));
            write(socket, burst.toByteArray());
            assertClosedByServer(socket);
        }
        Assert.assertEquals(serverCallback.messages.get(), 0);
    }

    @Test(timeOut = 20_000)
    public void repeatedHandshakeClosesConnection() throws Exception {
        try (Socket socket = connect()) {
            handshake(socket);
            write(socket, frame(serialize(new HandshakeMessage("again"))));
            assertClosedByServer(socket);
        }
        Assert.assertEquals(serverCallback.connects.get(), 1);
    }

    @Test(timeOut = 20_000)
    public void trustedObjectThatIsNotAMessageClosesConnection() throws Exception {
        try (Socket socket = connect()) {
            handshake(socket);
            write(socket, frame(serialize("plain string")));
            assertClosedByServer(socket);
        }
        waitUntil(() -> serverCallback.errors.stream().anyMatch(IllegalStateException.class::isInstance), "the error is reported");
        Assert.assertEquals(serverCallback.messages.get(), 0);
    }

    @Test(timeOut = 20_000)
    public void exceptionFromCallbackDoesNotCloseConnection() throws Exception {
        int callbackPort = freePort();
        BasicServer throwingServer = new BasicServer(callbackPort, new RecordingServerCallback() {
            @Override
            public void onMessage(Server server, Connection client, Message msg) {
                messages.incrementAndGet();
                throw new IllegalArgumentException("application bug");
            }
        }, sharedGroup, sharedGroup);
        throwingServer.settings().addTrustedPackage(ProtocolHardeningTest.class.getName() + "*");
        throwingServer.start();
        try (Socket socket = new Socket("127.0.0.1", callbackPort)) {
            handshake(socket);
            write(socket, frame(serialize(new TextMessage("boom"))));
            socket.setSoTimeout(500);
            Assert.assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read(new byte[1]));
        } finally {
            throwingServer.stop();
        }
    }

    @Test(timeOut = 20_000)
    public void sendFailureIsReportedToClientCallback() throws Exception {
        RecordingClientCallback callback = new RecordingClientCallback();
        Client client = connectClient(callback);

        client.send(new UnserializableMessage());

        waitUntil(() -> !callback.errors.isEmpty(), "the client reports the failure");
        Assert.assertTrue(client.isConnected(), "A local serialization failure must not end the connection");
    }

    @Test(timeOut = 20_000)
    public void oversizedSendIsReportedToClientCallback() throws Exception {
        RecordingClientCallback callback = new RecordingClientCallback();
        Client client = new Client("127.0.0.1", port, callback, sharedGroup);
        client.settings().setMaxFrameLength(1024);
        clients.add(client);
        client.connect();
        waitUntil(() -> callback.connects.get() == 1, "the client connects");

        client.send(new LargeMessage());

        waitUntil(() -> !callback.errors.isEmpty(), "the client reports the failure");
        Assert.assertTrue(client.isConnected());
    }

    @Test(timeOut = 20_000)
    public void serverStopClosesClientsOnSharedEventLoops() throws Exception {
        int sharedPort = freePort();
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup(1);
        BasicServer sharedServer = new BasicServer(sharedPort, new RecordingServerCallback(), boss, worker);
        sharedServer.start();
        try {
            RecordingClientCallback callback = new RecordingClientCallback();
            Client client = new Client("127.0.0.1", sharedPort, callback);
            clients.add(client);
            client.connect();
            waitUntil(() -> callback.connects.get() == 1, "the client connects");

            sharedServer.stop();

            waitUntil(() -> !client.isConnected(), "the client is disconnected");
            Assert.assertFalse(worker.isShuttingDown(), "Shared groups stay owned by the caller");
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    @Test(timeOut = 20_000)
    public void clientStopFromCallbackDoesNotDeadlock() throws Exception {
        CompletableFuture<Throwable> result = new CompletableFuture<>();
        Client client = new Client("127.0.0.1", port, new RecordingClientCallback() {
            @Override
            public void onConnect(Client client) {
                try {
                    client.stop();
                    result.complete(null);
                } catch (Throwable cause) {
                    result.complete(cause);
                }
            }
        });
        client.connect();

        Assert.assertNull(result.get(WAIT_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test(timeOut = 20_000)
    public void serverStopFromCallbackDoesNotDeadlock() throws Exception {
        int callbackPort = freePort();
        CompletableFuture<Throwable> result = new CompletableFuture<>();
        BasicServer stoppingServer = new BasicServer(callbackPort, new RecordingServerCallback() {
            @Override
            public void onConnect(Server server, Connection client) {
                try {
                    server.stop();
                    result.complete(null);
                } catch (Throwable cause) {
                    result.complete(cause);
                }
            }
        });
        stoppingServer.start();
        connectClient(new RecordingClientCallback(), callbackPort);

        Assert.assertNull(result.get(WAIT_MILLIS, TimeUnit.MILLISECONDS));
    }

    @DataProvider
    public Object[][] defaultTrustedValues() {
        Map<String, Integer> map = new HashMap<>();
        map.put("key", 1);
        return new Object[][]{
                {new ArrayList<>(List.of("a"))},
                {map},
                {new HashSet<>(List.of("a"))},
                {new LinkedHashMap<>(map)},
                {new LinkedHashSet<>(List.of("a"))},
                {new TreeMap<>(map)},
                {new TreeSet<>(List.of("a"))},
                {new LinkedList<>(List.of("a"))},
                {new BitSet()},
                {new Date(0)},
                {Locale.ENGLISH},
                {Map.of("key", 1)},
                {Set.of("a")},
                {List.of("a", "b")},
                {Arrays.asList("a", "b")},
                {Collections.unmodifiableList(new ArrayList<>(List.of("a")))},
                {UUID.randomUUID()},
                {42L},
        };
    }

    @Test(dataProvider = "defaultTrustedValues")
    public void defaultAllowlistPermitsCommonValueTypes(Object value) throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageDecoder(MessageDecoder.DEFAULT_TRUSTED_PACKAGES));
        channel.writeInbound(Unpooled.wrappedBuffer(frame(serialize((Serializable) value))));
        Assert.assertEquals(channel.<Object>readInbound(), value);
        channel.finishAndReleaseAll();
    }

    @Test(expectedExceptions = DecoderException.class)
    public void defaultAllowlistRejectsUnlistedJavaUtilClasses() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageDecoder(MessageDecoder.DEFAULT_TRUSTED_PACKAGES));
        channel.writeInbound(Unpooled.wrappedBuffer(frame(serialize(new java.util.Vector<>(List.of("a"))))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void handshakeTimeoutMustBePositive() {
        server.setHandshakeTimeout(Duration.ZERO);
    }

    private static HashSet<Object> nestedSets(int depth) {
        HashSet<Object> root = new HashSet<>();
        Set<Object> left = root;
        Set<Object> right = new HashSet<>();
        for (int i = 0; i < depth; i++) {
            Set<Object> nextLeft = new HashSet<>();
            Set<Object> nextRight = new HashSet<>();
            nextLeft.add("value");
            left.add(nextLeft);
            left.add(nextRight);
            right.add(nextLeft);
            right.add(nextRight);
            left = nextLeft;
            right = nextRight;
        }
        return root;
    }

    private Socket connect() throws IOException {
        return new Socket("127.0.0.1", port);
    }

    private Client connectClient() throws Exception {
        return connectClient(new RecordingClientCallback());
    }

    private Client connectClient(RecordingClientCallback callback) throws Exception {
        return connectClient(callback, port);
    }

    private Client connectClient(RecordingClientCallback callback, int targetPort) throws Exception {
        Client client = new Client("127.0.0.1", targetPort, callback, sharedGroup);
        clients.add(client);
        client.connect();
        waitUntil(() -> callback.connects.get() == 1, "the client connects");
        return client;
    }

    private static void handshake(Socket socket) throws Exception {
        write(socket, frame(serialize(new HandshakeMessage("raw-socket"))));
        socket.setSoTimeout((int) WAIT_MILLIS);
        byte[] lengthPrefix = socket.getInputStream().readNBytes(Integer.BYTES);
        byte[] ack = socket.getInputStream().readNBytes(ByteBuffer.wrap(lengthPrefix).getInt());
        Assert.assertTrue(ack.length > 0, "Expected a " + HandshakeAckMessage.class.getSimpleName());
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static byte[] frame(byte[] body) {
        return ByteBuffer.allocate(Integer.BYTES + body.length).putInt(body.length).put(body).array();
    }

    private static void write(Socket socket, byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private static void assertClosedByServer(Socket socket) throws IOException {
        socket.setSoTimeout((int) WAIT_MILLIS);
        try {
            while (socket.getInputStream().read() >= 0) {
                // Drain anything sent before the close
            }
        } catch (SocketTimeoutException timeout) {
            Assert.fail("Server did not close the connection");
        } catch (IOException reset) {
            // A connection reset also proves the server closed it
        }
    }
}
