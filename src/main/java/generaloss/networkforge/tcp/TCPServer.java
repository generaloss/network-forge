package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.codec.ConnectionCodecFactory;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.event.*;
import generaloss.networkforge.tcp.handler.EventHandlerPipeline;
import generaloss.networkforge.tcp.handler.EventListenerHolder;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;
import generaloss.networkforge.packet.NetPacket;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPServer {

    private static final String CLASS_NAME = TCPServer.class.getSimpleName();

    private ConnectionCodecFactory codecFactory;
    private TCPConnectionOptionsHolder initialOptions;
    private final SelectorLoop selectorLoop;

    private final EventHandlerPipeline eventHandlers;
    private final EventListenerHolder listeners;

    private final ConcurrentLinkedQueue<TCPConnection> connections;
    private int connectionCounter;

    private ServerSocketChannel[] serverChannels;

    public TCPServer(TCPConnectionOptionsHolder initialOptions) {
        this.setCodecFactory(CodecType.DEFAULT);
        this.setInitialOptions(initialOptions);

        this.selectorLoop = new SelectorLoop();
        this.connections = new ConcurrentLinkedQueue<>();

        this.listeners = new EventListenerHolder();
        this.listeners.registerOnDisconnect(
            (connection, reason, e) -> connections.remove(connection)
        );

        this.eventHandlers = new EventHandlerPipeline();
        this.eventHandlers.addHandler(listeners);
    }

    public TCPServer() {
        this(new TCPConnectionOptionsHolder());
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


    public EventHandlerPipeline getEventHandlers() {
        return eventHandlers;
    }


    public TCPServer registerOnConnect(ConnectionListener onConnect) {
        listeners.registerOnConnect(onConnect);
        return this;
    }

    public TCPServer registerOnDisconnect(CloseCallback onClose) {
        listeners.registerOnDisconnect(onClose);
        return this;
    }

    public TCPServer registerOnReceive(DataReceiver onReceive) {
        listeners.registerOnReceive(onReceive);
        return this;
    }

    public TCPServer registerOnReceiveStream(StreamDataReceiver onReceive) {
        listeners.registerOnReceiveStream(onReceive);
        return this;
    }

    public TCPServer registerOnError(ErrorHandler onError) {
        listeners.registerOnError(onError);
        return this;
    }


    public TCPServer unregisterOnConnect(ConnectionListener onConnect) {
        listeners.unregisterOnConnect(onConnect);
        return this;
    }

    public TCPServer unregisterOnDisconnect(CloseCallback onClose) {
        listeners.unregisterOnDisconnect(onClose);
        return this;
    }

    public TCPServer unregisterOnReceive(DataReceiver onReceive) {
        listeners.unregisterOnReceive(onReceive);
        return this;
    }

    public TCPServer unregisterOnError(ErrorHandler onError) {
        listeners.unregisterOnError(onError);
        return this;
    }


    public TCPServer run(InetAddress address, int... ports) throws IOException {
        if(ports.length < 1)
            throw new IllegalArgumentException("At least one port must be specified");

        if(this.isRunning())
            throw new IllegalStateException("TCP server is already running");

        connections.clear();
        selectorLoop.open();

        serverChannels = new ServerSocketChannel[ports.length];
        for(int i = 0; i < ports.length; i++) {
            final int port = ports[i];

            final ServerSocketChannel serverChannel = ServerSocketChannel.open();
            initialOptions.applyServerPreBind(serverChannel);

            try {
                serverChannel.bind(new InetSocketAddress(address, port));
            } catch (BindException e) {
                throw new BindException("Failed to bind TCP server to port " + port + ": " + e.getMessage());
            }

            serverChannel.configureBlocking(false);
            selectorLoop.registerAcceptKey(serverChannel);

            serverChannels[i] = serverChannel;
        }

        final String threadName = (CLASS_NAME + "-selector-thread-#" + this.hashCode());
        selectorLoop.startSelectionLoopThread(threadName, this::onKeySelected);

        return this;
    }

    public TCPServer run(String hostname, int... ports) throws IOException {
        return this.run(InetAddress.getByName(hostname), ports);
    }

    public TCPServer run(int... ports) throws IOException {
        return this.run("0.0.0.0", ports);
    }


    private void onKeySelected(SelectionKey key) {
        if(key.isReadable()){
            final TCPConnection connection = ((TCPConnection) key.attachment());
            connection.pushRead();
        }
        if(key.isWritable()){
            final TCPConnection connection = ((TCPConnection) key.attachment());
            connection.pushSend();
        }
        if(key.isAcceptable())
            this.acceptNewConnection((ServerSocketChannel) key.channel());
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
                throw new IllegalStateException("TCP connection codec factory returned null");

            final TCPConnection connection = new TCPConnection(channel, key, codec, eventHandlers);
            connection.setName(CLASS_NAME + "-connection-#" + this.hashCode() + "N" + (connectionCounter++));
            initialOptions.copyTo(connection.options());
            key.attach(connection);

            connections.add(connection);

            connection.pushConnect();
        } catch (IOException ignored){ }
    }


    public Collection<TCPConnection> getConnections() {
        return connections;
    }

    public boolean isRunning() {
        return (serverChannels != null && !serverChannels[0].socket().isClosed());
    }

    public boolean isClosed() {
        return (serverChannels == null || serverChannels[0].socket().isClosed());
    }

    public TCPServer close() {
        if(this.isClosed())
            return this;

        selectorLoop.close();

        for(TCPConnection connection : connections)
            connection.close(CloseReason.CLOSE_SERVER, null);
        connections.clear();

        for(ServerSocketChannel serverChannel : serverChannels)
            ResUtils.close(serverChannel);
        serverChannels = null;

        return this;
    }


    public int broadcast(byte[] byteArray) {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");

        int failedSends = 0;

        for(TCPConnection connection : connections)
            if(!connection.send(byteArray))
                failedSends++;

        return failedSends;
    }

    public int broadcast(TCPConnection except, byte[] byteArray) {
        if(except == null)
            throw new IllegalArgumentException("Argument 'except' cannot be null");
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");

        int failedSends = 0;

        for(TCPConnection connection : connections)
            if(connection != except)
                if(!connection.send(byteArray))
                    failedSends++;

        return failedSends;
    }

    public int broadcast(ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);

        return this.broadcast(byteArray);
    }

    public int broadcast(TCPConnection except, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);

        return this.broadcast(except, byteArray);
    }

    public int broadcast(String string) {
        if(string == null)
            throw new IllegalArgumentException("Agrument 'string' cannot be null");

        return this.broadcast(string.getBytes());
    }

    public int broadcast(TCPConnection except, String string) {
        if(string == null)
            throw new IllegalArgumentException("Agrument 'string' cannot be null");

        return this.broadcast(except, string.getBytes());
    }

    public int broadcast(BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Agrument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.broadcast(byteArray);
        } catch (IOException ignored) {
            return connections.size();
        }
    }

    public int broadcast(TCPConnection except, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Agrument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.broadcast(except, byteArray);
        } catch (IOException ignored) {
            return connections.size();
        }
    }

    public int broadcast(NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.broadcast(byteArray);
        } catch (IOException ignored) {
            return connections.size();
        }
    }

    public int broadcast(TCPConnection except, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.broadcast(except, byteArray);
        } catch (IOException ignored) {
            return connections.size();
        }
    }

}
