package generaloss.networkforge.tcp.listener;

public enum CloseReason {

    CLOSE_CONNECTION              ("Connection closed"                      ),
    CLOSE_CLIENT                  ("Client closed"                          ),
    CLOSE_SERVER                  ("Server closed"                          ),
    CLOSE_BY_OTHER_SIDE           ("Connection closed by the other side"    ),
    FRAME_READ_SIZE_LIMIT_EXCEEDED("Frame read size limit has been exceeded"),
    INVALID_FRAME_SIZE            ("Invalid packet size"                    ),
    INTERNAL_ERROR                ("Internal error occurred"                );

    private final String message;

    CloseReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasException() {
        return (this == INTERNAL_ERROR);
    }

    @Override
    public String toString() {
        return message;
    }

}
