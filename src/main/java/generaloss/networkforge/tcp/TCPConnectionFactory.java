package generaloss.networkforge.tcp;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

@FunctionalInterface
public interface TCPConnectionFactory {

    TCPConnection create(
        SocketChannel channel,
        SelectionKey selectionKey,
        TCPCloseable onClose
    );

}
