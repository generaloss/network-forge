package generaloss.networkforge.tcp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AsyncConnectState {

    public volatile boolean active;
    public volatile CompletableFuture<TCPConnection> future;
    public volatile boolean hasDeadline;
    public volatile long deadlineNanos;

    public void begin(long timeoutMillis) {
        if(timeoutMillis < 1) {
            hasDeadline = false;
        } else {
            hasDeadline = true;
            deadlineNanos = (System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
            // future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        this.future = new CompletableFuture<>();
        this.active = true;
    }

    private void close() {
        active = false;
        hasDeadline = false;
        deadlineNanos = 0L;
    }

    public void end() {
        future.cancel(true);
        this.close();
    }

    public void end(Exception e) {
        future.completeExceptionally(e);
        this.close();
    }

    public void end(TCPConnection connection) {
        future.complete(connection);
        this.close();
    }
    
}
