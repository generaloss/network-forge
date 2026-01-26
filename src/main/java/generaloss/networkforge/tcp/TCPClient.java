package generaloss.networkforge.tcp;

import generaloss.networkforge.ConnectState;
import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.pipeline.ListenersHolder;
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
import java.util.concurrent.TimeoutException;

public class TCPClient implements Sendable {

    private static final String CLASS_NAME = TCPClient.class.getSimpleName();

    private ConnectionCodec connectionCodec;
    private TCPConnectionOptionsHolder initialOptions;
    private final SelectorLoop selectorLoop;

    private final ListenersHolder listeners;
    private final EventPipeline eventPipeline;

    private TCPConnection connection;
    
    private volatile ConnectState state;
    private volatile boolean hasAsyncConnectDeadline;
    private volatile long asyncConnectDeadlineMillis;

    public TCPClient() {
        this.setCodec(CodecType.DEFAULT);

        this.initialOptions = new TCPConnectionOptionsHolder();
        this.selectorLoop = new SelectorLoop();

        this.listeners = new ListenersHolder();
        this.listeners.registerOnDisconnect(this::onConnectionClosed); // TCPConnection internal close

        this.eventPipeline = new EventPipeline();
        this.eventPipeline.addHandlerLast(listeners);
        
        this.state = ConnectState.CLOSED;
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


    public TCPClient registerOnConnect(ConnectListener onConnect) {
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

    public TCPClient registerOnError(ErrorListener onError) {
        listeners.registerOnError(onError);
        return this;
    }


    public TCPClient unregisterOnConnect(ConnectListener onConnect) {
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

    public TCPClient unregisterOnError(ErrorListener onError) {
        listeners.unregisterOnError(onError);
        return this;
    }


    public EventPipeline getEventPipeline() {
        return eventPipeline;
    }

    public TCPConnection getConnection() {
        return connection;
    }


    public TCPClient connect(SocketAddress socketAddress, int timeoutMillis) throws IOException, AlreadyConnectedException {
        if(state != ConnectState.CLOSED)
            throw new AlreadyConnectedException();

        state = ConnectState.CONNECTING;

        // channel
        final SocketChannel channel = SocketChannel.open();
        initialOptions.applyPreConnect(channel);

        // blocking connect
        channel.configureBlocking(true);
        channel.socket().connect(socketAddress, timeoutMillis);
        channel.configureBlocking(false);

        // create non-blocking connection
        selectorLoop.open();
        this.createConnection(channel);
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


    public TCPClient connectAsync(SocketAddress socketAddress, long timeoutMillis) throws IOException, AlreadyConnectedException {
        if(state != ConnectState.CLOSED)
            throw new AlreadyConnectedException();

        state = ConnectState.CONNECTING;
        System.out.println("connecting");

        // channel
        final SocketChannel channel = SocketChannel.open();
        initialOptions.applyPreConnect(channel);
        channel.configureBlocking(false);

        // try instant connect
        final boolean connected = channel.connect(socketAddress);
        selectorLoop.open();

        if(connected) {
            this.createConnection(channel);
        } else {
            selectorLoop.registerConnectKey(channel);

            if(timeoutMillis < 1) {
                hasAsyncConnectDeadline = false;
            } else {
                hasAsyncConnectDeadline = true;
                asyncConnectDeadlineMillis = (System.currentTimeMillis() + timeoutMillis);
            }
        }
        this.startSelectorLoop();

        return this;
    }

    public TCPClient connectAsync(SocketAddress socketAddress) throws IOException, AlreadyConnectedException  {
        return this.connectAsync(socketAddress, 0L);
    }

    public TCPClient connectAsync(String hostname, int port, long timeoutMillis) throws IOException, AlreadyConnectedException  {
        return this.connectAsync(new InetSocketAddress(hostname, port), timeoutMillis);
    }

    public TCPClient connectAsync(String hostname, int port) throws IOException, AlreadyConnectedException  {
        return this.connectAsync(hostname, port, 0L);
    }


    private void startSelectorLoop() {
        final String threadName = (CLASS_NAME + "-selector-thread-#" + this.hashCode());
        selectorLoop.startSelectionLoopThread(threadName, this::onKeySelected, this::getNextSelectionTimeout);
    }

    private long getNextSelectionTimeout() {
        if(!hasAsyncConnectDeadline) {
            System.out.println("next (default) timeout 0");
            return 0L;
        }

        final long timeLeft = (asyncConnectDeadlineMillis - System.currentTimeMillis());

        if(timeLeft <= 0) {
            if(state == ConnectState.CONNECTING) {
                System.out.println("closing");

                this.abortConnect();

                eventPipeline.fireOnError(0, null, ErrorSource.CONNECT, new TimeoutException());

                return 100L;
            }
            System.out.println("state is " + state);
            System.out.println("next (last) timeout 0 (" + timeLeft + " <= 0)");
            return 0L;
        }

        System.out.println("next (calculated) timeout " + timeLeft);
        return timeLeft;
    }

    private void onKeySelected(SelectionKey key) {
        if(state == ConnectState.CONNECTING && key.isConnectable()) {
            final SocketChannel channel = (SocketChannel) key.channel();

            try {
                channel.finishConnect();
                key.interestOpsAnd(~SelectionKey.OP_CONNECT);
                key.selector().wakeup();
                this.createConnection(channel);

            } catch (IOException e) {
                ResUtils.close(channel);
                this.abortConnect();
                eventPipeline.fireOnError(0, null, ErrorSource.CONNECT, e);
            }

        } else {
            connection.onKeySelected();
        }
    }

    private void createConnection(SocketChannel channel) throws IOException {
        initialOptions.applyPostConnect(channel);

        final SelectionKey key = selectorLoop.registerReadKey(channel);

        connection = new TCPConnection(channel, key, connectionCodec, eventPipeline);
        connection.setName(this.makeConnectionName());

        initialOptions.copyTo(connection.getOptions());

        state = ConnectState.CONNECTED;
        connection.onConnectOp();
    }

    private String makeConnectionName() {
        return (CLASS_NAME + "-connection-#" + this.hashCode());
    }


    public boolean isOpen() {
        return (state == ConnectState.CONNECTED);
    }

    public boolean isClosed() {
        return (state != ConnectState.CONNECTED);
    }

    public ConnectState getState() {
        return state;
    }


    public void close() {
        if(state == ConnectState.CONNECTED) {
            this.closeConnection();
        } else if(state == ConnectState.CONNECTING) {
            this.abortConnect();
        }
    }

    private void closeConnection() {
        state = ConnectState.CLOSING;
        connection.close(CloseReason.CLOSE_CLIENT, null); // will call onConnectionClosed(...)
        this.stop();
    }

    private void abortConnect() {
        state = ConnectState.CLOSING;
        this.stop();
    }

    private void onConnectionClosed(TCPConnection connection, CloseReason reason, Exception e) {
        // is it manual close call
        if(reason == CloseReason.CLOSE_CLIENT)
            return;

        // calls when occurs: TCPConnection internal error
        state = ConnectState.CLOSING;
        this.stop();
    }

    private void stop() {
        selectorLoop.close();
        state = ConnectState.CLOSED;
    }


    @Override
    public boolean send(byte[] data) {
        if(state != ConnectState.CONNECTED)
            return false;
        return connection.send(data);
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        if(state != ConnectState.CONNECTED)
            return false;
        return connection.send(buffer);
    }

    @Override
    public boolean send(String string) {
        if(state != ConnectState.CONNECTED)
            return false;
        return connection.send(string);
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        if(state != ConnectState.CONNECTED)
            return false;
        return connection.send(streamWriter);
    }

    @Override
    public boolean send(NetPacket<?> packet) {
        if(state != ConnectState.CONNECTED)
            return false;
        return connection.send(packet);
    }

}
