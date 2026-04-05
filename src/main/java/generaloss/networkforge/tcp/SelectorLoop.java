package generaloss.networkforge.tcp;

import generaloss.networkforge.SelectionKeyConsumer;
import generaloss.resourceflow.ResUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Set;

public class SelectorLoop {

    private final Object openLock;
    private volatile Selector selector;
    private volatile Thread selectorThread;

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
                try {
                    selectorThread.join();
                } catch (InterruptedException ignored) { }
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


    public void startLoopThread(String threadName, SelectionKeyConsumer onKeySelected) {
        if(selectorThread != null)
            return;

        selectorThread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    if(selector != null)
                        this.selectKeys(onKeySelected);
                } catch (ClosedSelectorException | CancelledKeyException | NullPointerException ignored) {
                } catch (Exception e) {
                    // noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }
            }
        }, threadName);

        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    public void selectKeys(SelectionKeyConsumer onKeySelected) throws Exception {
        try {
            // there's ~1% chance that the selector will not wake up
            // after the other side closes connection
            // so timeout is must have
            selector.select(); // 100L - OR ISN'T?
        } catch (IOException ignored) {
            return;
        }

        final Set<SelectionKey> selectedKeys = selector.selectedKeys();

        for(SelectionKey key : selectedKeys)
            if(key.isValid())
                onKeySelected.accept(key); // may throw any exception

        selectedKeys.clear();
    }

}
