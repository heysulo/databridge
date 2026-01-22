package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class PingPongClient implements Runnable, ClientCallback {
    private static final Logger logger = LoggerFactory.getLogger(PingPongClient.class);
    private final String host;
    private final int port;
    private final int clientId;
    private Client client;

    public PingPongClient(String host, int port, int clientId) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try {
            this.client = new Client(host, port, this);
            this.client.addTrustedPackage("dev.heysulo.databridge.examples.pingpong.**");
            this.client.connect();
        } catch (Exception e) {
            logger.error("[Client-{}] Startup Error", clientId, e);
        }
    }

    @Override
    public void OnConnect(Client client) {
        logger.info("[Client-{}] Connected", clientId);
        client.send(new PingMessage());
    }

    @Override
    public void OnDisconnect(Client client) {
        logger.info("[Client-{}] Disconnected", clientId);
    }

    @Override
    public void OnMessage(Client client, Message msg) {
        if (msg instanceof PongMessage) {
            PongMessage pong = (PongMessage) msg;
            long rtt = System.currentTimeMillis() - pong.originalTimestamp;
            logger.info("[Client-{}] RTT: {}ms, Server Random: {}", clientId, rtt, pong.randomValue);

            // Schedule next ping
            try {
                Thread.sleep(1000 + new Random().nextInt(1000));
                if (client.getWorkerPool() != null && !client.getWorkerPool().isShutdown()) {
                    client.send(new PingMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void OnError(Client client, Throwable cause) {
        logger.error("[Client-{}] Error", clientId, cause);
    }
}
