# DataBridge

DataBridge is a lightweight, Netty-based Java framework for secure, reliable object-based communication between clients and servers. It simplifies network programming by abstraction over Netty pipelines while providing built-in features like heartbeats, latency tracking, and secure object deserialization.

## Architecture

DataBridge is built on top of high-performance **Netty** I/O.

- **Client/Server Model**: Abstracted via `Client` and `Server` classes.
- **Protocol**: Custom protocol using Java Serialization with strict class whitelisting for security.
- **Security**: 
  - **SSL/TLS Support**: Built-in support via `SecureServer` and SSL contexts.
  - **Deserialization Security**: Whitelist-based packet filtering to prevent deserialization vulnerabilities.
- **Reliability**:
  - **Handshaking**: Version verification on connect.
  - **Heartbeats**: Automatic heartbeat messages to maintain connection health and measure latency.
  - **Callback API**: Event-driven architecture using `ClientCallback` and `ServerCallback`.

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven

### Installation

Clone the repository and build using Maven:

```bash
mvn clean install
```

## Usage

### 1. Define Messages

All messages must implement the `Message` interface and be Serializable.

```java
import dev.heysulo.databridge.core.common.Message;

public class MyMessage implements Message {
    private final String content;

    public MyMessage(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}
```

### 2. Implement Server

Implement `ServerCallback` to handle events and start a `BasicServer`.

```java
import dev.heysulo.databridge.core.server.BasicServer;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import dev.heysulo.databridge.core.common.Connection;
import dev.heysulo.databridge.core.common.Message;

public class MyServer {
    public static void main(String[] args) throws InterruptedException {
        ServerCallback callback = new ServerCallback() {
            @Override
            public void onConnect(Server server, Connection connection) {
                System.out.println("Client connected: " + connection.getRemoteAddress());
            }

            @Override
            public void onDisconnect(Server server, Connection connection) {
                System.out.println("Client disconnected");
            }

            @Override
            public void onMessage(Server server, Connection connection, Message message) {
                if (message instanceof MyMessage) {
                    System.out.println("Received: " + ((MyMessage) message).getContent());
                }
            }

            @Override
            public void onError(Server server, Connection connection, Throwable cause) {
                cause.printStackTrace();
            }
        };

        BasicServer server = new BasicServer(8080, callback);
        
        // IMPORTANT: Whitelist your message packages for security
        server.settings().addTrustedPackage("com.mycompany.messages.**");
        
        server.start();
    }
}
```

### 3. Implement Client

Implement `ClientCallback` and connect to the server.

```java
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.client.callback.ClientCallback;
import dev.heysulo.databridge.core.common.Message;

public class MyClient {
    public static void main(String[] args) throws InterruptedException {
        ClientCallback callback = new ClientCallback() {
            @Override
            public void onConnect(Client client) {
                System.out.println("Connected to server");
                client.send(new MyMessage("Hello World!"));
            }

            @Override
            public void onDisconnect(Client client) {
                System.out.println("Disconnected");
            }

            @Override
            public void onMessage(Client client, Message message) {
                // Handle responses
            }

            @Override
            public void onError(Client client, Throwable cause) {
                cause.printStackTrace();
            }
        };

        Client client = new Client("localhost", 8080, callback);
        client.connect();
    }
}
```

## Security Metrics

- **Trusted Packages**: By default, only `java.lang.*`, `dev.heysulo.**`, and a fixed set of common `java.util` collections and value types are allowed (see `MessageDecoder.DEFAULT_TRUSTED_PACKAGES`). Other `java.util` classes are rejected. Use `settings().addTrustedPackage()` on both the client and the server to allow your own domain classes.
- **Handshake**: Until the handshake completes, only handshake messages of up to 4 KB are accepted, so unauthenticated peers cannot reach the trusted packages. Connections that do not finish within `settings().setHandshakeTimeout()` (10 seconds by default) are closed, on both the client and the server.
- **Deserialization Limits**: Every frame is limited in object depth, reference count, size, and array length. A frame that breaks these limits or the allowlist closes the connection.
- **SSL**: Use `SecureServer` and pass an internal `SslContext` for encrypted communications. Clients check that the server certificate matches the address they connect to. `Client.setHostnameVerificationEnabled(false)` turns this off, but allows impersonation by anyone holding a trusted certificate.

## Per-Connection Callbacks

By default, events go to the callback given to the `Server` or `Client`. To give one connection its own handler, for example once you know which application it belongs to, install a `ConnectionCallback`:

```java
connection.setCallback(new MyApplicationHandler());
```

This works on both server-side connections and `Client`, and covers messages, errors, and disconnects from that point on. Pass `null` to restore the original callback.

## Error Handling

- `send()` queues the message and returns. Later failures, such as a message that cannot be serialized or exceeds the maximum frame length, are reported through `onError`.
- `stop()` and `disconnect()` can be called from callbacks. There they return without waiting, and shutdown finishes after the callback returns.
- Callbacks run on Netty event loops, so blocking in one delays every connection on that loop.

## Logging

DataBridge logs through [SLF4J](https://www.slf4j.org/), so add the binding of your choice to see its output. Lifecycle events are logged at `INFO`. Connections closed for protocol violations are logged at `WARN`, or at `DEBUG` if the peer never completed the handshake.

## Development

`mvn verify` builds the project, runs the tests, and runs Checkstyle and SpotBugs. The same command runs in CI on Java 17 and 21.

## License

DataBridge is licensed under the [Apache License, Version 2.0](LICENSE).
