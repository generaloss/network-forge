package generaloss.networkforge.tcp;

import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.networkforge.packet.NetPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TCPServer {

    private TCPConnection.Factory connectionFactory;

    private Consumer<TCPConnection> onConnect;
    private TCPCloseable onDisconnect;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;

    private final CopyOnWriteArrayList<TCPConnection> connections;
    private ServerSocketChannel[] serverChannels;
    private Thread selectorThread;
    private Selector selector;

    public TCPServer() {
        this.setConnectionType(TCPConnectionType.DEFAULT);
        this.setOnError(TCPErrorHandler::printErrorCatch);
        this.connections = new CopyOnWriteArrayList<>();
    }


    public TCPServer setConnectionType(Class<?> tcpConnectionClass) {
        this.connectionFactory = TCPConnection.getFactory(tcpConnectionClass);
        return this;
    }

    public TCPServer setConnectionType(TCPConnectionType connectionType) {
        this.connectionFactory = TCPConnection.getFactory(connectionType);
        return this;
    }


    public TCPServer setOnConnect(Consumer<TCPConnection> onConnect) {
        this.onConnect = onConnect;
        return this;
    }

    public TCPServer setOnDisconnect(TCPCloseable onDisconnect) {
        this.onDisconnect = onDisconnect;
        return this;
    }

    public TCPServer setOnReceive(TCPReceiver onReceive) {
        this.onReceive = onReceive;
        return this;
    }

    public TCPServer setOnReceiveStream(TCPReceiverStream onReceive) {
        this.onReceive = (sender, bytes) -> {
            try{
                final BinaryInputStream stream = new BinaryInputStream(bytes);
                onReceive.receive(sender, stream);
                stream.close();
            }catch(IOException e){
                throw new RuntimeException(e);
            }
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
        }catch(Exception e) {
            this.invokeOnError(connection, "onConnect callback", e);
        }
    }

    private void invokeOnDisconnect(TCPConnection connection, String message) {
        if(onDisconnect == null)
            return;

        try {
            onDisconnect.close(connection, message);
        }catch(Exception e) {
            this.invokeOnError(connection, "onDisconnect callback", e);
        }
    }

    private void invokeOnReceive(TCPConnection connection, byte[] bytes) {
        if(onReceive == null || bytes == null)
            return;

        try {
            onReceive.receive(connection, bytes);
        }catch(Exception e) {
            this.invokeOnError(connection, "onReceive callback", e);
        }
    }

    private void invokeOnError(TCPConnection connection, String source, Exception exception) {
        try {
            onError.error(connection, source, exception);
        }catch(Exception e) {
            TCPErrorHandler.printErrorCatch(connection, "onError callback", exception);
        }
    }


    public TCPServer run(InetAddress address, int... ports) {
        if(ports.length < 1)
            throw new IllegalArgumentException("At least one port must be specified");

        if(this.isRunning())
            throw new IllegalStateException("TCP server is already running");

        try{
            connections.clear();
            selector = Selector.open();

            serverChannels = new ServerSocketChannel[ports.length];
            for(int i = 0; i < ports.length; i++) {
                final int port = ports[i];

                final ServerSocketChannel channel = ServerSocketChannel.open();
                channel.bind(new InetSocketAddress(address, port));
                channel.configureBlocking(false);
                channel.register(selector, SelectionKey.OP_ACCEPT);
                serverChannels[i] = channel;
            }

            this.startSelectorThread();

        }catch(Exception e){
            throw new IllegalStateException("Failed to start TCP server: " + e.getMessage());
        }

        return this;
    }

    public TCPServer run(String hostname, int... ports) throws UnknownHostException {
        return this.run(InetAddress.getByName(hostname), ports);
    }

    public TCPServer run(int... ports) throws UnknownHostException {
        return this.run(InetAddress.getByName("0.0.0.0"), ports);
    }

    private void startSelectorThread() {
        selectorThread = new Thread(() -> {
            while(!Thread.interrupted() && !this.isClosed())
                this.selectKeys();
        }, "TCP server selector thread #" + this.hashCode());
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
            final byte[] bytes = connection.read(false);
            this.invokeOnReceive(connection, bytes);
        }
        if(key.isValid() && key.isWritable()){
            final TCPConnection connection = ((TCPConnection) key.attachment());
            connection.processWriteQueue(key);
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
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);

            final TCPConnection connection = connectionFactory.create(channel, key, this::onConnectionClosed);
            connections.add(connection);
            key.attach(connection);

            this.invokeOnConnect(connection);
        }catch(IOException ignored){ }
    }

    private void onConnectionClosed(TCPConnection connection, String message) {
        connections.remove(connection);
        this.invokeOnDisconnect(connection, message);
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
            connection.close("Server closed");
        connections.clear();

        for(ServerSocketChannel serverChannel: serverChannels)
            ResUtils.close(serverChannel);
        serverChannels = null;

        ResUtils.close(selector);
        return this;
    }


    public boolean broadcast(byte[] bytes) {
        boolean result = false;
        for(TCPConnection connection: connections)
            result |= connection.send(bytes);
        return result;
    }

    public boolean broadcast(TCPConnection except, byte[] bytes) {
        boolean result = false;
        for(TCPConnection connection: connections)
            if(connection != except)
                result |= connection.send(bytes);
        return result;
    }

    public boolean broadcast(NetPacket<?> packet) {
        return this.broadcast(stream -> {
            stream.writeShort(packet.getPacketID());
            packet.write(stream);
        });
    }

    public boolean broadcast(TCPConnection except, NetPacket<?> packet) {
        return this.broadcast(except, stream -> {
            stream.writeShort(packet.getPacketID());
            packet.write(stream);
        });
    }

    public boolean broadcast(BinaryStreamWriter streamWriter) {
        return this.broadcast(BinaryStreamWriter.writeBytes(streamWriter));
    }

    public boolean broadcast(TCPConnection except, BinaryStreamWriter streamWriter) {
        return this.broadcast(except, BinaryStreamWriter.writeBytes(streamWriter));
    }

}
