package generaloss.networkforge.tcp;

import generaloss.networkforge.SelectionKeyConsumer;
import generaloss.resourceflow.ResUtils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Set;

public class SelectorLoop {

    private Selector selector;
    private Thread selectorThread;

    public void open() throws IOException {
        selector = Selector.open();
    }

    public void close() {
        if(selector != null)
            selector.wakeup();

        if(selectorThread != null)
            selectorThread.interrupt();

        ResUtils.close(selector);
        selector = null;
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

    public void startSelectionLoopThread(String threadName, SelectionKeyConsumer selectedKeyConsumer) {
        selectorThread = new Thread(() -> {
            while(!Thread.interrupted()) {
                try {
                    final boolean selectResult = this.selectKeys(selectedKeyConsumer);
                    if(!selectResult)
                        break;
                } catch (Exception ignored) {
                    // ignore consumer exceptions
                }
            }
        }, threadName);

        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    public boolean selectKeys(long timeout, SelectionKeyConsumer selectedKeyConsumer) throws IOException {
        try {
            selector.select(timeout);
        } catch (IOException ignored) {
            return false;
        }

        final Set<SelectionKey> selectedKeys = selector.selectedKeys();
        for(SelectionKey key : selectedKeys)
            if(key.isValid())
                selectedKeyConsumer.accept(key);

        selectedKeys.clear();
        return true;
    }

    public boolean selectKeys(SelectionKeyConsumer selectedKeyConsumer) throws IOException {
        return this.selectKeys(0L, selectedKeyConsumer);
    }

}
