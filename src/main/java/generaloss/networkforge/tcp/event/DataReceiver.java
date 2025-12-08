package generaloss.networkforge.tcp.event;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface DataReceiver {
    
    void onReceive(TCPConnection connection, byte[] byteArray);

}
