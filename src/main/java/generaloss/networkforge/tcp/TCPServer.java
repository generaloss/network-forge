package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.networkforge.packet.NetPacket;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class TCPServer {

    private TCPConnectionFactory connectionFactory;
    private TCPConnectionOptionsHolder initialOptions;

    private Consumer<TCPConnection> onConnect;
    private TCPCloseable onClose;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;

    private final ConcurrentLinkedQueue<TCPConnection> connections;
    private int connectionCounter; // just for naming
    private ServerSocketChannel[] serverChannels;
    private Thread selectorThread;
    private Selector selector;

    public TCPServer(TCPConnectionOptionsHolder initialOptions) {
        this.setConnectionType(TCPConnectionType.DEFAULT);
        this.setOnError((connection, source, throwable) ->
            TCPErrorHandler.printErrorCatch(TCPServer.class, connection, source, throwable)
        );
        this.connections = new ConcurrentLinkedQueue<>();
        this.setInitialOptions(initialOptions);
    }

    public TCPServer() {
        this(new TCPConnectionOptionsHolder());
    }


    public TCPServer setConnectionType(Class<?> tcpConnectionClass) {
        this.connectionFactory = TCPConnection.getFactory(tcpConnectionClass);
        return this;
    }

    public TCPServer setConnectionType(TCPConnectionType connectionType) {
        this.connectionFactory = TCPConnection.getFactory(connectionType);
        return this;
    }


    public TCPConnectionOptionsHolder getInitialOptions() {
        return initialOptions;
    }

    public TCPServer setInitialOptions(TCPConnectionOptionsHolder initialOptions) {
        if(initialOptions == null)
            throw new IllegalArgumentException("initialOptions is null");
        this.initialOptions = initialOptions;
        return this;
    }


    public TCPServer setOnConnect(Consumer<TCPConnection> onConnect) {
        this.onConnect = onConnect;
        return this;
    }

    public TCPServer setOnDisconnect(TCPCloseable onClose) {
        this.onClose = onClose;
        return this;
    }

    public TCPServer setOnReceive(TCPReceiver onReceive) {
        this.onReceive = onReceive;
        return this;
    }

    public TCPServer setOnReceiveStream(TCPReceiverStream onReceive) {
        this.onReceive = (sender, bytes) -> {
            final BinaryInputStream stream = new BinaryInputStream(bytes);
            onReceive.receive(sender, stream);
            ResUtils.close(stream);
        };
        return this;
    }

    public TCPServer setOnError(TCPErrorHandler onError) {
        this.onError = onError;
        return this;
    }


    private void invokeOnConnect(TCPConnection connection) {
        if(onConnect == null)
            return;

        try {
            onConnect.accept(connection);
        }catch(Throwable onConnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.CONNECT_CALLBACK, onConnectThrowable);
        }
    }

    private void invokeOnDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        if(onClose == null)
            return;

        try {
            onClose.close(connection, reason, e);
        }catch(Throwable onCloseThrowable) {
            this.invokeOnError(connection, TCPErrorSource.DISCONNECT_CALLBACK, onCloseThrowable);
        }
    }

    private void invokeOnReceive(TCPConnection connection, byte[] bytes) {
        if(onReceive == null || bytes == null)
            return;

        try {
            onReceive.receive(connection, bytes);
        }catch(Throwable onReceiveThrowable) {
            this.invokeOnError(connection, TCPErrorSource.RECEIVE_CALLBACK, onReceiveThrowable);
        }
    }

    private void invokeOnError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        try {
            onError.error(connection, source, throwable);
        }catch(Throwable onErrorThrowable) {
            TCPErrorHandler.printErrorCatch(TCPServer.class, connection, TCPErrorSource.ERROR_CALLBACK, onErrorThrowable);
        }
    }


    public TCPServer run(InetAddress address, int... ports) throws IOException {
        if(ports.length < 1)
            throw new IllegalArgumentException("At least one port must be specified");

        if(this.isRunning())
            throw new IllegalStateException("TCP server is already running");

        connections.clear();
        selector = Selector.open();

        serverChannels = new ServerSocketChannel[ports.length];
        for(int i = 0; i < ports.length; i++) {
            final int port = ports[i];

            final ServerSocketChannel channel = ServerSocketChannel.open();
            initialOptions.applyServerPreBind(channel);

            try {
                channel.bind(new InetSocketAddress(address, port));
            }catch(BindException e) {
                throw new BindException("Failed to bind TCP server to port " + port + ": " + e.getMessage());
            }

            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_ACCEPT);

            serverChannels[i] = channel;
        }

        this.startSelectorThread();

        return this;
    }

    public TCPServer run(String hostname, int... ports) throws IOException {
        return this.run(InetAddress.getByName(hostname), ports);
    }

    public TCPServer run(int... ports) throws IOException {
        return this.run("0.0.0.0", ports);
    }

    private void startSelectorThread() {
        selectorThread = new Thread(() -> {
            while(!Thread.interrupted() && !this.isClosed())
                this.selectKeys();
        }, "TCP-server-selector-thread-#" + this.hashCode());
        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    private void selectKeys() {
        try{
            selector.select();
            final Set<SelectionKey> selectedKeys = selector.selectedKeys();
            for(SelectionKey key : selectedKeys)
                this.processKey(key);
            selectedKeys.clear();

        }catch(Exception ignored) { }
    }

    private void processKey(SelectionKey key) {
        if(key.isValid() && key.isReadable()){
            final TCPConnection connection = ((TCPConnection) key.attachment());
            final byte[] bytes = connection.read();
            this.invokeOnReceive(connection, bytes);
        }
        if(key.isValid() && key.isWritable()){
            final TCPConnection connection = ((TCPConnection) key.attachment());
            connection.processWriteKey(key);
        }
        if(key.isValid() && key.isAcceptable()){
            this.acceptNewConnection((ServerSocketChannel) key.channel());
        }
    }

    private void acceptNewConnection(ServerSocketChannel serverChannel) {
        try{
            final SocketChannel channel = serverChannel.accept();
            if(channel == null)
                return;

            channel.configureBlocking(false);
            initialOptions.applyPostConnect(channel);

            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);

            final TCPConnection connection = connectionFactory.create(channel, key, this::onConnectionClosed);
            connection.setName("TCPServer-connection-#" + (connectionCounter++));
            initialOptions.copyTo(connection.options());
            key.attach(connection);

            connections.add(connection);

            this.invokeOnConnect(connection);
        }catch(IOException ignored){ }
    }

    private void onConnectionClosed(TCPConnection connection, TCPCloseReason reason, Exception e) {
        connections.remove(connection);
        this.invokeOnDisconnect(connection, reason, e);
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

        if(selectorThread != null) {
            selectorThread.interrupt();
            selector.wakeup();
        }

        for(TCPConnection connection: connections)
            connection.close(TCPCloseReason.CLOSE_SERVER, null);
        connections.clear();

        for(ServerSocketChannel serverChannel: serverChannels)
            ResUtils.close(serverChannel);
        serverChannels = null;

        ResUtils.close(selector);
        return this;
    }


    public int broadcast(byte[] byteArray) {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' is null");

        int failedSends = 0;

        for(TCPConnection connection: connections)
            if(!connection.send(byteArray))
                failedSends++;

        return failedSends;
    }

    public int broadcast(TCPConnection except, byte[] byteArray) {
        if(except == null)
            throw new IllegalArgumentException("Argument 'except' is null");
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' is null");

        int failedSends = 0;

        for(TCPConnection connection: connections)
            if(connection != except)
                if(!connection.send(byteArray))
                    failedSends++;

        return failedSends;
    }

    public int broadcast(ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' is null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);

        return this.broadcast(byteArray);
    }

    public int broadcast(TCPConnection except, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' is null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);

        return this.broadcast(except, byteArray);
    }

    public int broadcast(String string) {
        if(string == null)
            throw new IllegalArgumentException("Agrument 'string' is null");

        return this.broadcast(string.getBytes());
    }

    public int broadcast(TCPConnection except, String string) {
        if(string == null)
            throw new IllegalArgumentException("Agrument 'string' is null");

        return this.broadcast(except, string.getBytes());
    }

    public int broadcast(BinaryStreamWriter streamWriter) {
        return this.broadcast(BinaryStreamWriter.writeBytes(streamWriter));
    }

    public int broadcast(TCPConnection except, BinaryStreamWriter streamWriter) {
        return this.broadcast(except, BinaryStreamWriter.writeBytes(streamWriter));
    }

    public int broadcast(NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' is null");

        return this.broadcast(packet.toByteArray());
    }

    public int broadcast(TCPConnection except, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' is null");

        return this.broadcast(except, packet.toByteArray());
    }

}
