package dev.heysulo.databridge.examples.pingpong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PingPongApp {
    private static final Logger logger = LoggerFactory.getLogger(PingPongApp.class);
    private static final int PORT = 9090;
    private static final int CLIENT_COUNT = 5;

    public static void main(String[] args) throws InterruptedException {
        PingPongServer server = new PingPongServer(PORT);
        
        new Thread(() -> {
            try {
                server.start();
            } catch (InterruptedException e) {
                logger.error("Server interrupted", e);
            }
        }).start();

        // Give server time to start
        Thread.sleep(1000);

        ExecutorService executor = Executors.newFixedThreadPool(CLIENT_COUNT);
        for (int i = 0; i < CLIENT_COUNT; i++) {
            executor.submit(new PingPongClient("localhost", PORT, i));
        }

        // Keep running for a while
        Thread.sleep(20000); // Increased time to observe logs
        logger.info("Shutting down application...");
        server.stop();
        executor.shutdownNow();
    }
}
