package generaloss.networkforge.tcp.event;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.resourceflow.stream.BinaryInputStream;

@FunctionalInterface
public interface StreamDataReceiver {

    void onReceive(TCPConnection connection, BinaryInputStream inStream);

}
