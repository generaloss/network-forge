package generaloss.networkforge.tcp.listener;

public enum ErrorSource {

    ACCEPT,
    SEND,
    BROADCAST,
    CLOSE,

    CONNECT_HANDLER,
    DISCONNECT_HANDLER,
    RECEIVE_HANDLER,
    ERROR_HANDLER,
    SEND_HANDLER,

}
