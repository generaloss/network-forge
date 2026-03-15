package generaloss.networkforge.tcp;

import generaloss.resourceflow.ResUtils;

import java.nio.channels.SocketChannel;

public class SyncSocketConnector {

    private volatile SocketChannel channel;

    public void set(SocketChannel channel) {
        this.channel = channel;
    }

    public void cancel() {
        ResUtils.close(channel);
        channel = null;
    }

    public SocketChannel getChannel() {
        return channel;
    }

}
