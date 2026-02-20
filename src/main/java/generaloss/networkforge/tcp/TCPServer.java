package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.codec.ConnectionCodecFactory;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.pipeline.EventPipeline;
import generaloss.networkforge.tcp.pipeline.ListenersHolder;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;
import generaloss.networkforge.packet.NetPacket;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPServer {

    private static final String CLASS_NAME = TCPServer.class.getSimpleName();

    private ConnectionCodecFactory codecFactory;
    private TCPConnectionOptionsHolder initialOptions;
    private final SelectorLoop selectorLoop;

    private final ConcurrentLinkedQueue<TCPConnection> connections;
    private final AtomicInteger connectionCounter;

    private final ListenersHolder listeners;
    private final EventPipeline eventPipeline;

    private ServerSocketChannel[] serverChannels;
    private int pendingConnectionsLimit;

    private volatile boolean running;

    public TCPServer() {
        this.setCodecFactory(CodecType.DEFAULT);

        this.initialOptions = new TCPConnectionOptionsHolder();
        this.selectorLoop = new SelectorLoop();
        this.connections = new ConcurrentLinkedQueue<>();
        this.connectionCounter = new AtomicInteger();

        this.listeners = new ListenersHolder();
        this.listeners.registerOnDisconnect(
            (connection, reason, e) -> connections.remove(connection)
        );

        this.eventPipeline = new EventPipeline(listeners);

        this.serverChannels = new ServerSocketChannel[0];
        this.pendingConnectionsLimit = 128;
    }


    public TCPServer setCodecFactory(ConnectionCodecFactory codecFactory) {
        if(codecFactory == null)
            throw new IllegalArgumentException("Argument 'codecFactory' cannot be null");

        this.codecFactory = codecFactory;
        return this;
    }

    public TCPServer setCodecFactory(CodecType codecType) {
        if(codecType == null)
            throw new IllegalArgumentException("Argument 'codecType' cannot be null");

        this.codecFactory = codecType.getFactory();
        return this;
    }


    public TCPConnectionOptionsHolder getInitialOptions() {
        return initialOptions;
    }

    public TCPServer setInitialOptions(TCPConnectionOptionsHolder initialOptions) {
        if(initialOptions == null)
            throw new IllegalArgumentException("Argument 'initialOptions' cannot be null");

        this.initialOptions = initialOptions;
        return this;
    }


    public EventPipeline getEventPipeline() {
        return eventPipeline;
    }


    public TCPServer registerOnConnect(ConnectListener onConnect) {
        listeners.registerOnConnect(onConnect);
        return this;
    }

    public TCPServer registerOnDisconnect(DisconnectListener onClose) {
        listeners.registerOnDisconnect(onClose);
        return this;
    }

    public TCPServer registerOnReceive(DataListener onReceive) {
        listeners.registerOnReceive(onReceive);
        return this;
    }

    public TCPServer registerOnError(ErrorListener onError) {
        listeners.registerOnError(onError);
        return this;
    }


    public TCPServer unregisterOnConnect(ConnectListener onConnect) {
        listeners.unregisterOnConnect(onConnect);
        return this;
    }

    public TCPServer unregisterOnDisconnect(DisconnectListener onClose) {
        listeners.unregisterOnDisconnect(onClose);
        return this;
    }

    public TCPServer unregisterOnReceive(DataListener onReceive) {
        listeners.unregisterOnReceive(onReceive);
        return this;
    }

    public TCPServer unregisterOnError(ErrorListener onError) {
        listeners.unregisterOnError(onError);
        return this;
    }


    public TCPServer setPendingConnectionsLimit(int pendingConnectionsLimit) {
        this.pendingConnectionsLimit = pendingConnectionsLimit;
        return this;
    }

    public int getPendingConnectionsLimit() {
        return pendingConnectionsLimit;
    }


    public TCPServer run(InetSocketAddress... addresses) throws IOException, IllegalStateException {
        if(addresses.length < 1)
            throw new IllegalArgumentException("At least one address must be specified");

        if(this.isRunning())
            throw new IllegalStateException("TCP server is already running");

        connections.clear();
        selectorLoop.open();

        serverChannels = new ServerSocketChannel[addresses.length];
        for(int i = 0; i < addresses.length; i++) {
            final InetSocketAddress address = addresses[i];

            final ServerSocketChannel serverChannel = ServerSocketChannel.open();
            initialOptions.applyServerPreBind(serverChannel);

            try {
                serverChannel.bind(address, pendingConnectionsLimit);
            } catch (BindException e) {
                throw new BindException("Failed to bind TCP server to address '" + address + "': " + e.getMessage());
            }

            serverChannel.configureBlocking(false);
            selectorLoop.registerAcceptKey(serverChannel);

            serverChannels[i] = serverChannel;
        }

        selectorLoop.startSelectionLoopThread(this.makeSelectorThreadName(), this::onKeySelected);

        running = true;
        return this;
    }

    private String makeSelectorThreadName() {
        return (CLASS_NAME + "-selector-thread-#" + this.hashCode());
    }

    public TCPServer run(String hostname, int... ports) throws IOException, IllegalStateException {
        final InetSocketAddress[] addresses = new InetSocketAddress[ports.length];
        for(int i = 0; i < ports.length; i++)
            addresses[i] = new InetSocketAddress(hostname, ports[i]);

        return this.run(addresses);
    }

    public TCPServer run(int... ports) throws IOException, IllegalStateException {
        return this.run("0.0.0.0", ports);
    }


    private void onKeySelected(SelectionKey key) {
        if(key.isAcceptable()) {
            this.acceptNewConnection((ServerSocketChannel) key.channel());
            return;
        }

        final TCPConnection connection = ((TCPConnection) key.attachment());
        connection.onKeySelected();
    }

    private void acceptNewConnection(ServerSocketChannel serverChannel) {
        try {
            final SocketChannel channel = serverChannel.accept();
            if(channel == null)
                return;

            channel.configureBlocking(false);
            initialOptions.applyPostConnect(channel);

            final SelectionKey key = selectorLoop.registerReadKey(channel);

            final ConnectionCodec codec = codecFactory.create();
            if(codec == null)
                throw new IllegalStateException("TCP-connection codec factory returned null");

            final TCPConnection connection = new TCPConnection(channel, key, codec, eventPipeline);
            connection.setName(this.makeConnectionName());
            initialOptions.copyTo(connection.getOptions());
            key.attach(connection);

            connections.add(connection);

            connection.onConnectOp();
        } catch (IOException e) {
            eventPipeline.fireError(0, null, ErrorSource.CONNECT, e);
        }
    }

    private String makeConnectionName() {
        final int number = connectionCounter.getAndIncrement();
        return (CLASS_NAME + "-connection-" + number);
    }


    public Collection<TCPConnection> getConnections() {
        return connections;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isClosed() {
        return !running;
    }

    public TCPServer close() {
        if(!running)
            return this;

        running = false;

        selectorLoop.close();

        for(TCPConnection connection : connections)
            connection.close(CloseReason.CLOSE_SERVER, null);
        connections.clear();

        for(ServerSocketChannel serverChannel : serverChannels)
            ResUtils.close(serverChannel);
        serverChannels = new ServerSocketChannel[0];

        return this;
    }


    public int broadcast(byte[] data) {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        int failedSends = 0;

        for(TCPConnection connection : connections)
            if(!connection.send(data))
                failedSends++;

        return failedSends;
    }

    public int broadcast(TCPConnection except, byte[] data) {
        if(except == null)
            throw new IllegalArgumentException("Argument 'except' cannot be null");
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        int failedSends = 0;

        for(TCPConnection connection : connections)
            if(connection != except)
                if(!connection.send(data))
                    failedSends++;

        return failedSends;
    }

    public int broadcast(ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);

        return this.broadcast(byteArray);
    }

    public int broadcast(TCPConnection except, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);

        return this.broadcast(except, byteArray);
    }

    public int broadcast(String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.broadcast(string.getBytes());
    }

    public int broadcast(TCPConnection except, String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.broadcast(except, string.getBytes());
    }

    public int broadcast(BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.broadcast(byteArray);

        } catch (IOException e) {
            eventPipeline.fireError(0, null, ErrorSource.BROADCAST, e);
            return connections.size();
        }
    }

    public int broadcast(TCPConnection except, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.broadcast(except, byteArray);

        } catch (IOException e) {
            eventPipeline.fireError(0, null, ErrorSource.BROADCAST, e);
            return connections.size();
        }
    }

    public int broadcast(NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.broadcast(byteArray);

        } catch (IOException e) {
            eventPipeline.fireError(0, null, ErrorSource.BROADCAST, e);
            return connections.size();
        }
    }

    public int broadcast(TCPConnection except, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.broadcast(except, byteArray);

        } catch (IOException e) {
            eventPipeline.fireError(0, null, ErrorSource.BROADCAST, e);
            return connections.size();
        }
    }

}
