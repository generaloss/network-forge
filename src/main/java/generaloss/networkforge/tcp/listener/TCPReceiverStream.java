package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.resourceflow.stream.BinaryInputStream;

@FunctionalInterface
public interface TCPReceiverStream {

    void receive(TCPConnection connection, BinaryInputStream inStream);

}
