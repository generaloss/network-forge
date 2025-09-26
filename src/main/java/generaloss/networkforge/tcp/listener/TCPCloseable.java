package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface TCPCloseable {

    void close(TCPConnection connection, TCPCloseCause TCPCloseCause, Exception e);

}
