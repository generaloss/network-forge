package generaloss.networkforge.tcp;

import java.nio.channels.SocketChannel;

public class AsyncConnectInfo extends ConnectInfo {

    public AsyncConnectInfo(SocketChannel channel) {
        super(channel);
    }

}
