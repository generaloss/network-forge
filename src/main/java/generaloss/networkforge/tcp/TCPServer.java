package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.iohandler.ConnectionIOHandler;
import generaloss.networkforge.tcp.iohandler.IOHandlerFactory;
import generaloss.networkforge.tcp.iohandler.IOHandlerType;
import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.tcp.processor.TCPProcessorPipeline;
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

    private IOHandlerFactory ioHandlerFactory;
    private TCPConnectionOptionsHolder initialOptions;
    private final TCPEventDispatcher eventDispatcher;
    private final SelectorController selectorController;

    private final ConcurrentLinkedQueue<TCPConnection> connections;
    private int connectionCounter;

    private ServerSocketChannel[] serverChannels;

    public TCPServer(TCPConnectionOptionsHolder initialOptions) {
        this.setIOHandlerType(IOHandlerType.DEFAULT);
        this.setInitialOptions(initialOptions);

        this.eventDispatcher = new TCPEventDispatcher();
        this.selectorController = new SelectorController();

        this.setOnError((connection, source, throwable) ->
            TCPErrorHandler.printErrorCatch(TCPServer.class.getSimpleName(), connection, source, throwable)
        );
        this.connections = new ConcurrentLinkedQueue<>();
    }

    public TCPServer() {
        this(new TCPConnectionOptionsHolder());
    }


    public TCPServer setIOHandlerFactory(IOHandlerFactory ioHandlerFactory) {
        if(ioHandlerFactory == null)
            throw new IllegalArgumentException("Argument 'ioHandlerFactory' cannot be null");

        this.ioHandlerFactory = ioHandlerFactory;
        return this;
    }

    public TCPServer setIOHandlerType(IOHandlerType connectionType) {
        if(connectionType == null)
            throw new IllegalArgumentException("Argument 'connectionType' cannot be null");

        this.ioHandlerFactory = connectionType.getFactory();
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


    public TCPServer setOnConnect(TCPConnectable onConnect) {
        eventDispatcher.setOnConnect(onConnect);
        return this;
    }

    public TCPServer setOnDisconnect(TCPCloseable onClose) {
        eventDispatcher.setOnDisconnect(onClose);
        return this;
    }

    public TCPServer setOnReceive(TCPReceiver onReceive) {
        eventDispatcher.setOnReceive(onReceive);
        return this;
    }

    public TCPServer setOnReceiveStream(TCPReceiverStream onReceive) {
        eventDispatcher.setOnReceiveStream(onReceive);
        return this;
    }

    public TCPServer setOnError(TCPErrorHandler onError) {
        eventDispatcher.setOnError(onError);
        return this;
    }


    public TCPProcessorPipeline getProcessorPipeline() {
        return eventDispatcher.getProcessorPipeline();
    }


    public TCPServer run(InetAddress address, int... ports) throws IOException {
        if(ports.length < 1)
            throw new IllegalArgumentException("At least one port must be specified");

        if(this.isRunning())
            throw new IllegalStateException("TCP server is already running");

        connections.clear();
        selectorController.open();

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
            selectorController.registerAcceptKey(serverChannel);

            serverChannels[i] = serverChannel;
        }

        final String threadName = (this.getClass().getSimpleName() + "-selector-thread-#" + this.hashCode());
        selectorController.startSelectionLoopThread(threadName, this::onKeySelected);

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
            final byte[] byteArray = connection.read();
            if(byteArray != null)
                eventDispatcher.invokeOnReceive(connection, byteArray);
        }
        if(key.isWritable()){
            final TCPConnection connection = ((TCPConnection) key.attachment());
            connection.processWriteKey(key);
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

            final SelectionKey key = selectorController.registerReadKey(channel);
            final ConnectionIOHandler ioHandler = ioHandlerFactory.create();
            if(ioHandler == null)
                throw new IllegalStateException("IOHandlerFactory returned null");

            final TCPConnection connection = new TCPConnection(channel, key, this::onConnectionClosed, ioHandler);
            connection.setName("TCPServer-connection-#" + this.hashCode() + "N" + (connectionCounter++));
            initialOptions.copyTo(connection.options());
            key.attach(connection);

            connections.add(connection);

            eventDispatcher.invokeOnConnect(connection);
        } catch (IOException ignored){ }
    }

    private void onConnectionClosed(TCPConnection connection, TCPCloseReason reason, Exception e) {
        connections.remove(connection);
        eventDispatcher.invokeOnDisconnect(connection, reason, e);
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

        selectorController.close();

        for(TCPConnection connection : connections)
            connection.close(TCPCloseReason.CLOSE_SERVER, null);
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
