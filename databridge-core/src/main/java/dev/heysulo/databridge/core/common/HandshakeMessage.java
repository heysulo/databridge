package dev.heysulo.databridge.core.common;

/**
 * Sent immediately upon connection to verify protocol compatibility.
 */
public final class HandshakeMessage implements Message {
    public static final int CURRENT_VERSION = 1;
    private static final long serialVersionUID = 1L;

    private final int version;
    private final String clientId;

    public HandshakeMessage(String clientId) {
        this.version = CURRENT_VERSION;
        this.clientId = clientId;
    }

    public int getVersion() {
        return version;
    }

    public String getClientId() {
        return clientId;
    }
}
