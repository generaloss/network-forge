package generaloss.networkforge.tcp;

import generaloss.resourceflow.ResUtils;

import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class AsyncSocketConnector {

    private final SocketChannel channel;
    private final boolean hasDeadline;
    private final long deadlineMillis;
    private final CompletableFuture<TCPConnection> resultFuture;

    public AsyncSocketConnector(SocketChannel channel, long timeoutMillis) {
        this.channel = channel;
        this.hasDeadline = (timeoutMillis > 0L);
        this.deadlineMillis = (System.currentTimeMillis() + timeoutMillis);
        this.resultFuture = new CompletableFuture<>();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public boolean hasDeadline() {
        return hasDeadline;
    }

    public long getDeadlineMillis() {
        return deadlineMillis;
    }

    public CompletableFuture<TCPConnection> getResultFuture() {
        return resultFuture;
    }


    public void cancel() {
        ResUtils.close(channel);
        resultFuture.cancel(true);
    }

}
