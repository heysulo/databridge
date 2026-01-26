package dev.heysulo.databridge.core.common;

/**
 * Sent immediately upon connection to verify protocol compatibility.
 */
public class HandshakeMessage implements Message {
    public static final int CURRENT_VERSION = 1;
    private static final long serialVersionUID = 1L;
    public final int version;
    public final String clientId;

    public HandshakeMessage(String clientId) {
        this.version = CURRENT_VERSION;
        this.clientId = clientId;
    }
}
