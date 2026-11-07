package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.Message;
import dev.heysulo.databridge.core.server.BasicServer;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class PingPongServer implements ServerCallback {
    private static final Logger logger = LoggerFactory.getLogger(PingPongServer.class);
    private static final int MAX_RANDOM_VALUE = 100;
    private final BasicServer server;
    private final Random random = new Random();

    public PingPongServer(int port) {
        this.server = new BasicServer(port, this);
        this.server.settings().addTrustedPackage("dev.heysulo.databridge.examples.pingpong.**");
    }

    public void start() throws InterruptedException {
        logger.info("Starting server...");
        server.start();
        logger.info("Server started.");
    }

    public void stop() throws InterruptedException {
        logger.info("Stopping server...");
        server.stop();
        logger.info("Server stopped.");
    }

    @Override
    public void onConnect(Server server, Connection client) {
        logger.info("Client connected: {}", client);
    }

    @Override
    public void onDisconnect(Server server, Connection client) {
        logger.info("Client disconnected");
    }

    @Override
    public void onMessage(Server server, Connection client, Message msg) {
        if (msg instanceof PingMessage) {
            PingMessage ping = (PingMessage) msg;
            int rand = random.nextInt(MAX_RANDOM_VALUE);
            logger.debug("Received Ping from {}, replying with random {}", client, rand);
            client.send(new PongMessage(ping.getTimestamp(), rand));
        }
    }

    @Override
    public void onError(Server server, Connection client, Throwable cause) {
        logger.error("Error with client {}", client, cause);
    }
}
