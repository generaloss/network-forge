package generaloss.networkforge.tcp;

import generaloss.networkforge.SelectionKeyConsumer;
import generaloss.resourceflow.ResUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Set;
import java.util.function.LongSupplier;

public class SelectorLoop {

    private volatile Selector selector;
    private volatile Thread selectorThread;
    private final Object openLock;

    public SelectorLoop() {
        this.openLock = new Object();
    }


    public void open() throws IOException {
        synchronized (openLock) {
            if(selector == null)
                selector = Selector.open();
        }
    }

    public void close() {
        synchronized (openLock) {
            if(selectorThread != null)
                selectorThread.interrupt();

            if(selector != null)
                selector.wakeup();

            // wait interrupted
            if(selectorThread != null) {
                System.out.println("  wait thread interrupt");
                try {
                    selectorThread.join();
                } catch(InterruptedException ignored) { }
                System.out.println("  thread interrupted");
            }

            ResUtils.close(selector);
            selector = null;
            selectorThread = null;
        }
    }

    private SelectionKey registerKey(AbstractSelectableChannel channel, int ops) throws ClosedChannelException {
        if(selector == null)
            throw new IllegalStateException("Selector is closed");
        return channel.register(selector, ops);
    }

    public SelectionKey registerAcceptKey(AbstractSelectableChannel channel) throws ClosedChannelException {
        return this.registerKey(channel, SelectionKey.OP_ACCEPT);
    }

    public SelectionKey registerConnectKey(SocketChannel channel) throws ClosedChannelException {
        return this.registerKey(channel, SelectionKey.OP_CONNECT);
    }

    public SelectionKey registerReadKey(SocketChannel channel) throws ClosedChannelException {
        return this.registerKey(channel, SelectionKey.OP_READ);
    }


    public void startSelectionLoopThread(String threadName, SelectionKeyConsumer onKeySelected, LongSupplier nextTimeoutGetter) {
        if(selectorThread != null)
            return;

        selectorThread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    this.selectKeys(onKeySelected, nextTimeoutGetter);
                } catch(ClosedSelectorException | CancelledKeyException | NullPointerException ignored) {
                } catch (Exception e) {
                    // noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }
            }
        }, threadName);

        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    public void startSelectionLoopThread(String threadName, SelectionKeyConsumer onKeySelected) {
        final LongSupplier defaultTimeoutGetter = () -> 0L;
        this.startSelectionLoopThread(threadName, onKeySelected, defaultTimeoutGetter);
    }

    public void selectKeys(SelectionKeyConsumer onKeySelected, LongSupplier nextTimeoutGetter) throws Exception {
        if(selector == null)
            return;

        try {
            final long timeoutMillis = nextTimeoutGetter.getAsLong();
            if(timeoutMillis > 0L) {
                selector.select(timeoutMillis);
            } else {
                selector.select();
            }
        } catch (IOException ignored) {
            return;
        }

        if(selector == null)
            return;

        final Set<SelectionKey> selectedKeys = selector.selectedKeys();
        for(SelectionKey key : selectedKeys)
            if(key.isValid())
                onKeySelected.accept(key); // may throw any exception

        selectedKeys.clear();
    }

}
