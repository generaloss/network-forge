package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface DisconnectListener {

    void onDisconnect(TCPConnection connection, CloseReason reason, Exception e);

}
