package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface ConnectListener {

    void onConnect(TCPConnection connection);

}
