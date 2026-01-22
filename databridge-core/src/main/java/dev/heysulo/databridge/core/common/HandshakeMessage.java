package dev.heysulo.databridge.core.common;

/**
 * Sent immediately upon connection to verify protocol compatibility.
 */
public class HandshakeMessage implements Message {
    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 1;

    public final int version;
    public final String clientId;

    public HandshakeMessage(String clientId) {
        this.version = CURRENT_VERSION;
        this.clientId = clientId;
    }
}
