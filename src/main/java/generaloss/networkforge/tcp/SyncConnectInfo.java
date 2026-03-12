package generaloss.networkforge.tcp;

import java.nio.channels.SocketChannel;

public class SyncConnectInfo extends ConnectInfo {

    public SyncConnectInfo(SocketChannel channel) {
        super(channel);
    }

}
