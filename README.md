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
    public String content;

    public MyMessage(String content) {
        this.content = content;
    }
}
```

### 2. Implement Server

Implement `ServerCallback` to handle events and start a `BasicServer`.

```java
import dev.heysulo.databridge.core.server.BasicServer;
import dev.heysulo.databridge.core.server.Server;
import dev.heysulo.databridge.core.server.callback.ServerCallback;
import dev.heysulo.databridge.core.client.Client;
import dev.heysulo.databridge.core.common.Message;

public class MyServer {
    public static void main(String[] args) throws InterruptedException {
        ServerCallback callback = new ServerCallback() {
            @Override
            public void OnConnect(Server server, Client client) {
                System.out.println("Client connected: " + client);
            }

            @Override
            public void OnDisconnect(Server server, Client client) {
                System.out.println("Client disconnected");
            }

            @Override
            public void OnMessage(Server server, Client client, Message msg) {
                if (msg instanceof MyMessage) {
                    System.out.println("Received: " + ((MyMessage) msg).content);
                }
            }

            @Override
            public void OnError(Server server, Client client, Throwable cause) {
                cause.printStackTrace();
            }
        };

        BasicServer server = new BasicServer(8080, callback);
        
        // IMPORTANT: Whitelist your message packages for security
        server.addTrustedPackage("com.mycompany.messages.**");
        
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
            public void OnConnect(Client client) {
                System.out.println("Connected to server");
                client.send(new MyMessage("Hello World!"));
            }

            @Override
            public void OnDisconnect(Client client) {
                System.out.println("Disconnected");
            }

            @Override
            public void OnMessage(Client client, Message msg) {
                // Handle responses
            }

            @Override
            public void OnError(Client client, Throwable cause) {
                cause.printStackTrace();
            }
        };

        Client client = new Client("localhost", 8080, callback);
        client.connect();
    }
}
```

## Security Metrics

- **Trusted Packages**: By default, only `java.lang.*`, `java.util.*`, and `dev.heysulo.*` are allowed. Use `addTrustedPackage()` to allow your own domain classes.
- **SSL**: Use `SecureServer` and pass an internal `SslContext` for encrypted communications.
