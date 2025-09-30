package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface TCPCloseable {

    void close(TCPConnection connection, TCPCloseReason reason, Exception e);

}
