package generaloss.networkforge.tcp.listener;

public enum CloseReason {

    ASYNC_CONNECT_ERROR            (true,  "Asynchronous connect error occurred"    , true),
    ASYNC_CONNECT_TIMEOUT          (true,  "Asynchronous connect timeout"           , false),
    CLOSE_CONNECTION               (false, "Connection closed"                      , false),
    CLOSE_CLIENT                   (false, "Client closed"                          , false),
    CLOSE_SERVER                   (false, "Server closed"                          , false),
    CLOSE_BY_OTHER_SIDE            (false, "Connection closed by the other side"    , false),
    FRAME_READ_SIZE_LIMIT_EXCEEDED (true,  "Frame read size limit has been exceeded", false),
    INVALID_FRAME_SIZE             (true,  "Invalid packet size"                    , false),
    INTERNAL_ERROR                 (true,  "Internal error occurred"                , true);

    private final boolean isError;
    private final String message;
    private final boolean hasException;

    CloseReason(boolean isError, String message, boolean hasException) {
        this.isError = isError;
        this.message = message;
        this.hasException = hasException;
    }

    public boolean isError() {
        return isError;
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
