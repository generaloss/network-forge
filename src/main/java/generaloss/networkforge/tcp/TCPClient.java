package generaloss.networkforge.tcp;

import generaloss.networkforge.ConnectionState;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.codec.ConnectionCodecFactory;
import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.listener.ListenersHolder;
import generaloss.networkforge.tcp.pipeline.EventPipeline;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TCPClient implements Sendable {

    private static final String CLASS_NAME = TCPClient.class.getSimpleName();

    private volatile ConnectionCodecFactory codecFactory;
    private volatile TCPConnectionOptionsHolder initialOptions;

    private final ListenersHolder listeners;
    private final EventPipeline eventPipeline;
    private final SelectorLoop selectorLoop;

    private volatile ConnectionState state;
    private volatile TCPConnection connection;

    public TCPClient() {
        this.listeners = new ListenersHolder();
        this.eventPipeline = new EventPipeline(listeners);
        this.selectorLoop = new SelectorLoop();
        this.state = ConnectionState.CLOSED;

        this.setCodec(CodecType.DEFAULT);
        this.setInitialOptions(new TCPConnectionOptionsHolder());
        this.registerOnDisconnect(this::onDisconnect);
    }


    public synchronized TCPClient connect(SocketAddress address, long timeoutMs) throws IOException, AlreadyConnectedException, TimeoutException {
        if(state != ConnectionState.CLOSED)
            throw new AlreadyConnectedException();

        state = ConnectionState.CONNECTING;
        final SocketChannel channel = this.connectChannel(address, timeoutMs);
        this.estabilishConnection(channel);
        return this;
    }

    public TCPClient connect(SocketAddress address) throws IOException, AlreadyConnectedException, TimeoutException {
        return this.connect(address, 0);
    }

    public TCPClient connect(String hostname, int port, long timeoutMs) throws IOException, AlreadyConnectedException, TimeoutException {
        return this.connect(new InetSocketAddress(hostname, port), timeoutMs);
    }

    public TCPClient connect(String hostname, int port) throws IOException, AlreadyConnectedException, TimeoutException {
        return this.connect(hostname, port, 0);
    }


    public synchronized CompletableFuture<TCPConnection> connectAsync(SocketAddress address, long timeoutMs) throws AlreadyConnectedException {
        if(this.isNotClosed())
            throw new AlreadyConnectedException();

        state = ConnectionState.CONNECTING;

        final CompletableFuture<TCPConnection> future = new CompletableFuture<>();

        new Thread(() -> {
            SocketChannel channel = null;

            try {
                channel = this.connectChannel(address, timeoutMs);
                this.estabilishConnection(channel);
                future.complete(connection);

            } catch (Exception e) {
                ResUtils.close(channel);
                future.completeExceptionally(e);
            }

        }, CLASS_NAME + "-async-connect").start();

        return future;
    }

    public CompletableFuture<TCPConnection> connectAsync(SocketAddress address) throws AlreadyConnectedException {
        return this.connectAsync(address, 0L);
    }

    public CompletableFuture<TCPConnection> connectAsync(String hostname, int port, long timeoutMs) throws AlreadyConnectedException {
        return this.connectAsync(new InetSocketAddress(hostname, port), timeoutMs);
    }

    public CompletableFuture<TCPConnection> connectAsync(String hostname, int port) throws AlreadyConnectedException {
        return this.connectAsync(hostname, port, 0L);
    }


    private synchronized SocketChannel connectChannel(SocketAddress address, long timeoutMs) throws IOException, TimeoutException {
        final SocketChannel channel = SocketChannel.open();
        initialOptions.applyPreConnect(channel);
        channel.configureBlocking(false);

        channel.connect(address);

        final long deadlineMs = (timeoutMs > 0L) ? (System.currentTimeMillis() + timeoutMs) : Long.MAX_VALUE;

        while(!channel.finishConnect()) {
            if(timeoutMs > 0L && System.currentTimeMillis() >= deadlineMs)
                throw new TimeoutException();

            Thread.onSpinWait();
        }

        if(!channel.isOpen())
            throw new IOException("Channel closed during connect");

        return channel;
    }

    private void estabilishConnection(SocketChannel channel) throws IOException {
        initialOptions.applyPostConnect(channel);

        selectorLoop.open();

        final SelectionKey key = selectorLoop.registerReadKey(channel);

        connection = new TCPConnection(channel, key, codecFactory, eventPipeline);
        final String name = (CLASS_NAME + "-connection-#" + this.hashCode());
        connection.setName(name);
        initialOptions.copyTo(connection.getOptions());

        final String threadName = (CLASS_NAME + "-selector-thread-#" + this.hashCode());
        selectorLoop.startLoopThread(threadName, this::onKeySelected);

        state = ConnectionState.CONNECTED;
        connection.onConnected();
    }

    private void onKeySelected(SelectionKey _key) {
        if(connection != null)
            connection.onKeySelected();
    }


    public synchronized CompletableFuture<TCPConnection> connectFastest(SocketAddress[] addresses, long timeoutMs) throws AlreadyConnectedException {
        if(this.isNotClosed())
            throw new AlreadyConnectedException();

        if(addresses == null || addresses.length == 0)
            throw new IllegalArgumentException("Addresses is empty");

        state = ConnectionState.CONNECTING;

        final CompletableFuture<TCPConnection> result = new CompletableFuture<>();
        final SocketChannel[] channels = new SocketChannel[addresses.length];
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicInteger remaining = new AtomicInteger(addresses.length);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();

        for(int i = 0; i < addresses.length; i++) {
            try {
                final SocketChannel channel = SocketChannel.open();
                initialOptions.applyPreConnect(channel);
                channel.configureBlocking(false);
                channel.connect(addresses[i]);
                channels[i] = channel;

            } catch(Throwable e) {
                firstError.compareAndSet(null, e);
                remaining.decrementAndGet();
            }
        }

        if(remaining.get() == 0) {
            Throwable error = firstError.get();
            if(error == null)
                error = new IOException("All connect attempts failed");

            state = ConnectionState.CLOSED;
            return CompletableFuture.failedFuture(error);
        }

        for(final SocketChannel channel : channels) {
            if(channel == null)
                continue;

            new Thread(() -> {
                try {
                    final long deadlineMs = (timeoutMs > 0L) ? (System.currentTimeMillis() + timeoutMs) : Long.MAX_VALUE;

                    while(!channel.finishConnect()) {
                        if(completed.get()) {
                            ResUtils.close(channel);
                            return;
                        }

                        if(timeoutMs > 0L && System.currentTimeMillis() >= deadlineMs)
                            throw new TimeoutException();

                        Thread.onSpinWait();
                    }

                    if(!channel.isOpen())
                        throw new IOException("Channel closed during connect");

                    if(completed.compareAndSet(false, true)) {
                        for(SocketChannel other : channels)
                            if(other != null && other != channel)
                                ResUtils.close(other);

                        this.estabilishConnection(channel);
                        result.complete(connection);
                    } else {
                        ResUtils.close(channel);
                    }

                } catch(Throwable e) {
                    firstError.compareAndSet(null, e);
                    ResUtils.close(channel);

                } finally {
                    if(remaining.decrementAndGet() == 0 && !completed.get()) {
                        Throwable error = firstError.get();
                        if(error == null)
                            error = new IOException("All connect attempts failed");

                        result.completeExceptionally(error);
                    }
                }

            }, CLASS_NAME + "-fastest-connect").start();
        }

        return result;
    }

    public CompletableFuture<TCPConnection> connectFastest(SocketAddress[] addresses) throws AlreadyConnectedException {
        return this.connectFastest(addresses, 0L);
    }


    public synchronized void close() {
        if(state != ConnectionState.CONNECTED)
            return;

        state = ConnectionState.CLOSING;

        selectorLoop.close();
        connection.close(CloseReason.CLOSE_CLIENT);
        connection = null;

        state = ConnectionState.CLOSED;
    }

    private synchronized void onDisconnect(TCPConnection _connection, CloseReason _reason) {
        state = ConnectionState.CLOSING;

        selectorLoop.close();
        connection = null;

        state = ConnectionState.CLOSED;
    }


    public ConnectionState getState() {
        return state;
    }

    public boolean isClosed() {
        return (state == ConnectionState.CLOSED);
    }

    public boolean isConnected() {
        return (state == ConnectionState.CONNECTED);
    }

    public boolean isNotClosed() {
        return (state != ConnectionState.CLOSED);
    }

    public boolean isNotConnected() {
        return (state != ConnectionState.CONNECTED);
    }


    public TCPClient setCodec(ConnectionCodecFactory codecFactory) {
        if(codecFactory == null)
            throw new IllegalArgumentException("Argument 'codecFactory' cannot be null");

        this.codecFactory = codecFactory;
        return this;
    }

    public TCPClient setCodec(CodecType codecType) {
        if(codecType == null)
            throw new IllegalArgumentException("Argument 'codecType' cannot be null");

        this.codecFactory = codecType.getFactory();
        return this;
    }


    public TCPConnectionOptionsHolder getInitialOptions() {
        return initialOptions;
    }

    public TCPClient setInitialOptions(TCPConnectionOptionsHolder initialOptions) {
        if(initialOptions == null)
            throw new IllegalArgumentException("Argument 'initialOptions' cannot be null");

        this.initialOptions = initialOptions;
        return this;
    }


    public EventPipeline getEventPipeline() {
        return eventPipeline;
    }

    public TCPConnection getConnection() {
        return connection;
    }


    public TCPClient registerOnConnect(TCPConnectionConsumer onConnect) {
        listeners.registerOnConnect(onConnect);
        return this;
    }

    public TCPClient registerOnDisconnect(DisconnectListener onClose) {
        listeners.registerOnDisconnect(onClose);
        return this;
    }

    public TCPClient registerOnReceive(DataListener onReceive) {
        listeners.registerOnReceive(onReceive);
        return this;
    }

    public TCPClient registerOnReadComplete(TCPConnectionConsumer onReadComplete) {
        listeners.registerOnReadComplete(onReadComplete);
        return this;
    }

    public TCPClient registerOnError(ErrorListener onError) {
        listeners.registerOnError(onError);
        return this;
    }

    public TCPClient registerOnSend(DataListener onSend) {
        listeners.registerOnSend(onSend);
        return this;
    }


    public TCPClient unregisterOnConnect(TCPConnectionConsumer onConnect) {
        listeners.unregisterOnConnect(onConnect);
        return this;
    }

    public TCPClient unregisterOnDisconnect(DisconnectListener onClose) {
        listeners.unregisterOnDisconnect(onClose);
        return this;
    }

    public TCPClient unregisterOnReceive(DataListener onReceive) {
        listeners.unregisterOnReceive(onReceive);
        return this;
    }

    public TCPClient unregisterOnReadComplete(TCPConnectionConsumer onReadComplete) {
        listeners.unregisterOnReadComplete(onReadComplete);
        return this;
    }

    public TCPClient unregisterOnError(ErrorListener onError) {
        listeners.unregisterOnError(onError);
        return this;
    }

    public TCPClient unregisterOnSend(DataListener onSend) {
        listeners.unregisterOnSend(onSend);
        return this;
    }


    public void awaitWriteDrain(long timeoutMs) throws InterruptedException {
        if(state == ConnectionState.CONNECTED)
            connection.awaitWriteDrain(timeoutMs);
    }


    @Override
    public boolean send(byte[] data) {
        if(state == ConnectionState.CONNECTED)
            return connection.send(data);
        return false;
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        if(state == ConnectionState.CONNECTED)
            return connection.send(buffer);
        return false;
    }

    @Override
    public boolean send(String string) {
        if(state == ConnectionState.CONNECTED)
            return connection.send(string);
        return false;
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        if(state == ConnectionState.CONNECTED)
            return connection.send(streamWriter);
        return false;
    }

    @Override
    public boolean send(NetPacket packet) {
        if(state == ConnectionState.CONNECTED)
            return connection.send(packet);
        return false;
    }

}
