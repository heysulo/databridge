package dev.heysulo.databridge.core.common;

/**
 * Sent by the server in response to a HandshakeMessage to confirm compatibility.
 */
public final class HandshakeAckMessage implements Message {
    private static final long serialVersionUID = 1L;
    private static final String ACCEPTED_MESSAGE = "OK";

    private final int version;
    private final boolean success;
    private final String message;

    private HandshakeAckMessage(boolean success, String message) {
        this.version = HandshakeMessage.CURRENT_VERSION;
        this.success = success;
        this.message = message;
    }

    public static HandshakeAckMessage accepted() {
        return new HandshakeAckMessage(true, ACCEPTED_MESSAGE);
    }

    public static HandshakeAckMessage rejected(String reason) {
        return new HandshakeAckMessage(false, reason);
    }

    public int getVersion() {
        return version;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
