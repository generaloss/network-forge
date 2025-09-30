package generaloss.networkforge.tcp.listener;

public enum TCPCloseReason {

    CLOSE_CONNECTION           ("Connection closed"                  , false),
    CLOSE_CLIENT               ("Client closed"                      , false),
    CLOSE_SERVER               ("Server closed"                      , false),
    CLOSE_BY_OTHER_SIDE        ("Connection closed by the other side", false),
    PACKET_SIZE_LIMIT_EXCEEDED ("Packet size limit exceeded"         , false),
    INVALID_PACKET_SIZE        ("Invalid packet size"                , false),
    INTERNAL_ERROR             ("Internal error occurred"            , true);

    private final String message;
    private final boolean hasException;

    TCPCloseReason(String message, boolean hasException) {
        this.message = message;
        this.hasException = hasException;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasException() {
        return hasException;
    }

    @Override
    public String toString() {
        return message;
    }

}
