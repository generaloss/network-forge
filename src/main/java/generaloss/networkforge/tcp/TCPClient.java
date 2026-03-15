package generaloss.networkforge.tcp;

import generaloss.networkforge.ConnectionState;
import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.listener.ListenersHolder;
import generaloss.networkforge.tcp.pipeline.EventPipeline;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class TCPClient implements Sendable {

    private static final String CLASS_NAME = TCPClient.class.getSimpleName();

    private ConnectionCodec connectionCodec;
    private TCPConnectionOptionsHolder initialOptions;
    private final SelectorLoop selectorLoop;

    private final ListenersHolder listeners;
    private final EventPipeline eventPipeline;

    private volatile TCPConnection connection;
    
    private volatile ConnectionState state;
    private final SyncSocketConnector syncConnector;
    private final Map<SocketChannel, AsyncSocketConnector> asyncConnectors;

    public TCPClient() {
        this.setCodec(CodecType.DEFAULT);

        this.initialOptions = new TCPConnectionOptionsHolder();
        this.selectorLoop = new SelectorLoop();

        this.listeners = new ListenersHolder();
        this.listeners.registerOnDisconnect(this::onConnectionClosed); // TCPConnection internal close

        this.eventPipeline = new EventPipeline(listeners);

        this.state = ConnectionState.CLOSED;
        this.syncConnector = new SyncSocketConnector();
        this.asyncConnectors = new ConcurrentHashMap<>();
    }


    public TCPClient connect(SocketAddress socketAddress, int timeoutMillis) throws IOException, AlreadyConnectedException {
        if(state != ConnectionState.CLOSED)
            throw new AlreadyConnectedException();

        state = ConnectionState.CONNECTING;

        // channel
        final SocketChannel channel = SocketChannel.open();
        initialOptions.applyPreConnect(channel);

        syncConnector.set(channel);

        // blocking connect
        channel.configureBlocking(true);
        channel.socket().connect(socketAddress, timeoutMillis);
        channel.configureBlocking(false);

        // create non-blocking connection
        selectorLoop.open();
        this.createTCPConnection(channel);
        this.startSelectorLoop();

        return this;
    }

    public TCPClient connect(SocketAddress socketAddress) throws IOException, AlreadyConnectedException  {
        return this.connect(socketAddress, 0);
    }

    public TCPClient connect(String hostname, int port, int timeoutMillis) throws IOException, AlreadyConnectedException  {
        return this.connect(new InetSocketAddress(hostname, port), timeoutMillis);
    }

    public TCPClient connect(String hostname, int port) throws IOException, AlreadyConnectedException  {
        return this.connect(hostname, port, 0);
    }


    public CompletableFuture<TCPConnection> connectAsync(SocketAddress socketAddress, long timeoutMillis) throws IOException, AlreadyConnectedException {
        if(state != ConnectionState.CLOSED)
            throw new AlreadyConnectedException();

        state = ConnectionState.CONNECTING;

        // channel
        final SocketChannel channel = SocketChannel.open();
        initialOptions.applyPreConnect(channel);
        channel.configureBlocking(false);

        final AsyncSocketConnector connector = new AsyncSocketConnector(channel, timeoutMillis);
        asyncConnectors.put(channel, connector);
        System.out.println("  connectAsync() created async connector (" + asyncConnectors.size() + ")");

        // try instant connect
        final boolean connected = channel.connect(socketAddress);
        selectorLoop.open();

        if(connected) {
            this.createTCPConnection(channel);
            this.completeConnector(connector, connection);
            System.out.println("  instant connect");
        } else {
            selectorLoop.registerConnectKey(channel);
            System.out.println(" register connect key");
        }
        this.startSelectorLoop();
        System.out.println("  start selector loop");

        return connector.getResultFuture();
    }

    public CompletableFuture<TCPConnection> connectAsync(SocketAddress socketAddress) throws IOException, AlreadyConnectedException  {
        return this.connectAsync(socketAddress, 0L);
    }

    public CompletableFuture<TCPConnection> connectAsync(String hostname, int port, long timeoutMillis) throws IOException, AlreadyConnectedException  {
        return this.connectAsync(new InetSocketAddress(hostname, port), timeoutMillis);
    }

    public CompletableFuture<TCPConnection> connectAsync(String hostname, int port) throws IOException, AlreadyConnectedException  {
        return this.connectAsync(hostname, port, 0L);
    }


    private void startSelectorLoop() {
        final String threadName = (CLASS_NAME + "-selector-thread-#" + this.hashCode());
        selectorLoop.startSelectionLoopThread(threadName, this::onKeySelected, this::getNextSelectionTimeout);
    }

    private long getNextSelectionTimeout() {
        System.out.println("  getNextSelectionTimeout()");
        long minMillisLeft = Long.MAX_VALUE;
        for(AsyncSocketConnector connector : asyncConnectors.values()) {
            if(!connector.hasDeadline())
                continue;

            final long millisLeft = (connector.getDeadlineMillis() - System.currentTimeMillis());

            System.out.println("  getNextSelectionTimeout() connector " + connector + " left=" + millisLeft + "ms with state=" + state);

            if(millisLeft <= 0L) {
                if(state == ConnectionState.CONNECTING) {
                    asyncConnectors.remove(connector.getChannel());
                    System.out.println("  getNextSelectionTimeout() removed async connector (" + asyncConnectors.size() + ")");

                    if(asyncConnectors.isEmpty()) {
                        connector.getResultFuture().completeExceptionally(new TimeoutException());
                        this.close();
                        System.out.println("  getNextSelectionTimeout() future exeception + null");
                    }
                }
                continue;
            }

            minMillisLeft = Math.min(minMillisLeft, millisLeft);
        }
        if(minMillisLeft == Long.MAX_VALUE)
            return 0L;

        System.out.println("  getNextSelectionTimeout() next selector timeout: " + minMillisLeft);

        return minMillisLeft;
    }

    private void onKeySelected(SelectionKey key) {
        System.out.println("  onKeySelected(" + key.toString() + ") {");
        if(state == ConnectionState.CONNECTED) {
            System.out.println("    onKeySelected() stage CONNECT-ED");
            connection.onKeySelected();
        }
        else if(state == ConnectionState.CONNECTING && key.isConnectable()) {
            System.out.println("    onKeySelected() stage CONNECT-ING");
            final SocketChannel channel = (SocketChannel) key.channel();
            final AsyncSocketConnector connector = asyncConnectors.get(channel);

            try {
                System.out.println("      onKeySelected() [try] finishConnect()");
                channel.finishConnect();

                if(!channel.isConnected() || !channel.isOpen()) {
                    System.out.println("      onKeySelected() [try] finishConnect() failed");
                    asyncConnectors.remove(channel);

                    if(asyncConnectors.isEmpty()) {
                        connector.getResultFuture().completeExceptionally(new IOException("Connection closed during connect"));
                        System.out.println("      onKeySelected() [try] future exception + null");
                    }
                    return;
                }

                System.out.println("      onKeySelected() [try] finishConnect() success");

                key.interestOpsAnd(~SelectionKey.OP_CONNECT);
                key.selector().wakeup();
                this.createTCPConnection(channel);
                this.completeConnector(connector, connection);
                System.out.println("      onKeySelected() [try] future complete! + null");

            } catch (IOException e) {
                System.out.println("      onKeySelected() [catch]");
                eventPipeline.fireError(null, ErrorSource.CONNECT, e);

                asyncConnectors.remove(channel);
                System.out.println("        onKeySelected() [catch] removed async connector (" + asyncConnectors.size() + ")");

                if(asyncConnectors.isEmpty()) {
                    connector.getResultFuture().completeExceptionally(e);
                    System.out.println("        onKeySelected() [catch] future exeption + null");
                }
            }
        }
    }

    private void createTCPConnection(SocketChannel channel) throws IOException {
        initialOptions.applyPostConnect(channel);

        final SelectionKey key = selectorLoop.registerReadKey(channel);

        connection = new TCPConnection(channel, key, connectionCodec, eventPipeline);
        final String name = (CLASS_NAME + "-connection-#" + this.hashCode());
        connection.setName(name);
        initialOptions.copyTo(connection.getOptions());

        state = ConnectionState.CONNECTED;
        connection.onConnected();
    }


    private void completeConnector(AsyncSocketConnector connector, TCPConnection connection) {
        connector.getResultFuture().complete(connection);
        asyncConnectors.remove(connector.getChannel());
        this.clearConnectors();
    }

    private void clearConnectors() {
        syncConnector.cancel();

        for(AsyncSocketConnector connector : asyncConnectors.values())
            connector.cancel();
        System.out.println("  clearConnectors() cleared async connectors " + asyncConnectors.size());
        asyncConnectors.clear();
    }


    public void close() {
        if(state == ConnectionState.CONNECTED) {
            System.out.println("  close() close connection");
            state = ConnectionState.CLOSING;

            connection.close(CloseReason.CLOSE_CLIENT, null); // will call onConnectionClosed(...)

            this.flushState();
        } else if(state == ConnectionState.CONNECTING) {
            System.out.println("  close() abort connection");
            state = ConnectionState.CLOSING;

            this.clearConnectors();
            System.out.println("  close() future cancel + null");

            this.flushState();
        } else
            System.out.println("  close() executed when " + state);
    }

    private void onConnectionClosed(TCPConnection connection, CloseReason reason, Exception e) {
        // client close call
        if(reason == CloseReason.CLOSE_CLIENT)
            return;

        // close by other side / error
        state = ConnectionState.CLOSING;
        this.flushState();
    }

    private void flushState() {
        selectorLoop.close();
        connection = null;
        state = ConnectionState.CLOSED;
        System.out.println("    flushState()");
    }


    public ConnectionState getState() {
        return state;
    }

    public boolean isOpen() {
        return (state == ConnectionState.CONNECTED);
    }

    public boolean isClosed() {
        return (state != ConnectionState.CONNECTED);
    }


    public TCPClient setCodec(ConnectionCodec connectionCodec) {
        if(connectionCodec == null)
            throw new IllegalArgumentException("Argument 'connectionCodec' cannot be null");

        this.connectionCodec = connectionCodec;
        return this;
    }

    public TCPClient setCodec(CodecType codecType) {
        if(codecType == null)
            throw new IllegalArgumentException("Argument 'codecType' cannot be null");

        this.connectionCodec = codecType.getFactory().create();
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


    public void awaitWriteDrain(long timeoutMillis) throws InterruptedException {
        if(state == ConnectionState.CONNECTED)
            connection.awaitWriteDrain(timeoutMillis);
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
