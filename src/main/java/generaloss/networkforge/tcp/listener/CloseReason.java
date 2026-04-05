package generaloss.networkforge.tcp.listener;

public enum CloseReason {

    CLOSE_CONNECTION               (false, "Close connection"                       ),
    CLOSE_CLIENT                   (false, "Close client"                           ),
    CLOSE_SERVER                   (false, "Close server"                           ),
    CLOSE_BY_OTHER_SIDE            (false, "Connection closed by the other side"    ),
    FRAME_READ_SIZE_LIMIT_EXCEEDED (true,  "Frame read size limit has been exceeded"),
    INVALID_FRAME_SIZE             (true,  "Invalid packet size"                    ),
    INTERNAL_ERROR                 (true,  "Internal error occurred"                );

    private final boolean isError;
    private final String message;

    CloseReason(boolean isError, String message) {
        this.isError = isError;
        this.message = message;
    }

    public boolean isError() {
        return isError;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message;
    }

}
