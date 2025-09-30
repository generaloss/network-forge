package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface TCPReceiver {
    
    void receive(TCPConnection connection, byte[] byteArray);

}
