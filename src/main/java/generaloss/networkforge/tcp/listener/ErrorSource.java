package generaloss.networkforge.tcp.listener;

public enum ErrorSource {

    CONNECT,
    SELECTOR_READ,
    SELECTOR_WRITE,
    BROADCAST,

    CONNECT_HANDLER,
    DISCONNECT_HANDLER,
    RECEIVE_HANDLER,
    READ_COMPLETE_HANDLER,
    ERROR_HANDLER,
    SEND_HANDLER,

}
