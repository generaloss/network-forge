package generaloss.networkforge.tcp;

import generaloss.resourceflow.stream.BinaryInputStream;

@FunctionalInterface
public interface TCPReceiverStream {

    void receive(TCPConnection sender, BinaryInputStream stream);

}
