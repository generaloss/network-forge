package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface DataListener {
    
    void onData(TCPConnection connection, byte[] data);

}
