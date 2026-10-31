package dev.heysulo.databridge.core.common;

/**
 * Sent by the server in response to a HandshakeMessage to confirm compatibility.
 */
public class HandshakeAckMessage implements Message {
    private static final long serialVersionUID = 1L;

    public final int version;
    public final boolean success;
    public final String message;

    public HandshakeAckMessage() {
        this(HandshakeMessage.CURRENT_VERSION, true, "OK");
    }

    public HandshakeAckMessage(int version, boolean success, String message) {
        this.version = version;
        this.success = success;
        this.message = message;
    }
}
