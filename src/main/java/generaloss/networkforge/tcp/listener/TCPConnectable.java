package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface TCPConnectable {

    void connect(TCPConnection connection);

}
