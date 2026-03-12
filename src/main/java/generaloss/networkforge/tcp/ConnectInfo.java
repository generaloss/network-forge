package generaloss.networkforge.tcp;

import java.nio.channels.SocketChannel;

public abstract class ConnectInfo {

    private final SocketChannel channel;

    public ConnectInfo(SocketChannel channel) {
        this.channel = channel;
    }

    public SocketChannel getChannel() {
        return channel;
    }

}
