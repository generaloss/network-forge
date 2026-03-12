package generaloss.networkforge.tcp.listener;

public enum ErrorSource {

    CONNECT,
    READ,
    BROADCAST,

    CONNECT_HANDLER,
    DISCONNECT_HANDLER,
    RECEIVE_HANDLER,
    READ_COMPLETE_HANDLER,
    ERROR_HANDLER,
    SEND_HANDLER,

}
