package dev.heysulo.databridge.examples.pingpong;

import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class PingPongClient implements Runnable, ClientCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingPongClient.class);
    private static final long MIN_PING_DELAY_MILLIS = 1000;
    private static final long PING_DELAY_JITTER_MILLIS = 1000;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "ping-scheduler");
        thread.setDaemon(true);
        return thread;
    });

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
            this.client.settings().addTrustedPackage("dev.heysulo.databridge.examples.pingpong.**");
            this.client.connect();
        } catch (Exception e) {
            LOGGER.error("[Client-{}] Startup Error", clientId, e);
        }
    }

    @Override
    public void onConnect(Client client) {
        LOGGER.info("[Client-{}] Connected", clientId);
        client.send(new PingMessage());
    }

    @Override
    public void onDisconnect(Client client) {
        LOGGER.info("[Client-{}] Disconnected", clientId);
    }

    @Override
    public void onMessage(Client client, Message msg) {
        if (msg instanceof PongMessage) {
            PongMessage pong = (PongMessage) msg;
            long rtt = System.currentTimeMillis() - pong.getOriginalTimestamp();
            LOGGER.info("[Client-{}] RTT: {}ms, Server Random: {}", clientId, rtt, pong.getRandomValue());
            scheduleNextPing(client);
        }
    }

    @Override
    public void onError(Client client, Throwable cause) {
        LOGGER.error("[Client-{}] Error", clientId, cause);
    }

    // Callbacks run on the event loop, so the delay must not block here
    private void scheduleNextPing(Client client) {
        long delayMillis = MIN_PING_DELAY_MILLIS + ThreadLocalRandom.current().nextLong(PING_DELAY_JITTER_MILLIS);
        SCHEDULER.schedule(() -> client.send(new PingMessage()), delayMillis, TimeUnit.MILLISECONDS);
    }
}
