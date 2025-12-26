package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface DataListener {
    
    void onReceive(TCPConnection connection, byte[] data);

}
