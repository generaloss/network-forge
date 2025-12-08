package generaloss.networkforge.tcp.event;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface ConnectionListener {

    void onConnect(TCPConnection connection);

}
