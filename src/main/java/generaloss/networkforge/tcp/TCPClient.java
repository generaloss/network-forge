package generaloss.networkforge.tcp;

import generaloss.networkforge.CipherPair;
import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.event.*;
import generaloss.networkforge.tcp.handler.EventListenerHolder;
import generaloss.networkforge.tcp.handler.EventHandlerPipeline;
import generaloss.networkforge.tcp.options.TCPConnectionOptions;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import javax.crypto.Cipher;
import java.io.IOException;
import java.net.ConnectException;
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

    private final EventHandlerPipeline eventHandlers;
    private final EventListenerHolder listeners;

    private TCPConnection connection;

    public TCPClient(TCPConnectionOptionsHolder initialOptions) {
        this.setCodec(CodecType.DEFAULT);
        this.setInitialOptions(initialOptions);

        this.selectorLoop = new SelectorLoop();

        this.listeners = new EventListenerHolder();
        this.eventHandlers = new EventHandlerPipeline();
        this.eventHandlers.addHandler(listeners);
    }

    public TCPClient() {
        this(new TCPConnectionOptionsHolder());
    }


    public TCPConnection getConnection() {
        return connection;
    }

    public TCPConnectionOptions getOptions() {
        if(connection == null)
            return null;
        return connection.options;
    }

    public CipherPair getCiphers() {
        if(connection == null)
            return null;
        return connection.ciphers;
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


    public EventHandlerPipeline getEventHandlers() {
        return eventHandlers;
    }


    public TCPClient registerOnConnect(ConnectionListener onConnect) {
        listeners.registerOnConnect(onConnect);
        return this;
    }

    public TCPClient registerOnDisconnect(CloseCallback onClose) {
        listeners.registerOnDisconnect(onClose);
        return this;
    }

    public TCPClient registerOnReceive(DataReceiver onReceive) {
        listeners.registerOnReceive(onReceive);
        return this;
    }

    public TCPClient registerOnReceiveStream(StreamDataReceiver onReceive) {
        listeners.registerOnReceiveStream(onReceive);
        return this;
    }

    public TCPClient registerOnError(ErrorHandler onError) {
        listeners.registerOnError(onError);
        return this;
    }


    public TCPClient unregisterOnConnect(ConnectionListener onConnect) {
        listeners.unregisterOnConnect(onConnect);
        return this;
    }

    public TCPClient unregisterOnDisconnect(CloseCallback onClose) {
        listeners.unregisterOnDisconnect(onClose);
        return this;
    }

    public TCPClient unregisterOnReceive(DataReceiver onReceive) {
        listeners.unregisterOnReceive(onReceive);
        return this;
    }

    public TCPClient unregisterOnError(ErrorHandler onError) {
        listeners.unregisterOnError(onError);
        return this;
    }


    public TCPClient connect(SocketAddress socketAddress, long timeoutMillis) throws IOException, TimeoutException {
        if(this.isConnected())
            throw new AlreadyConnectedException();

        selectorLoop.close();

        // channel
        final SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        initialOptions.applyPreConnect(channel);

        final boolean connectedInstantly = channel.connect(socketAddress);
        selectorLoop.open();

        if(connectedInstantly) {
            this.createConnection(channel);
            return this;
        }

        // wait for connection
        selectorLoop.registerConnectKey(channel);
        final boolean selectResult = selectorLoop.selectKeys(selectedKey -> {
            if(selectedKey.isConnectable() && channel.finishConnect()) {
                this.createConnection(channel);
            }else{
                channel.close();
                throw new ConnectException("Connection failed");
            }
        });
        if(!selectResult) {
            channel.close();
            throw new TimeoutException("Connection timed out after " + timeoutMillis + " ms");
        }

        return this;
    }

    public TCPClient connect(SocketAddress socketAddress) throws IOException, TimeoutException  {
        return this.connect(socketAddress, 0L);
    }

    public TCPClient connect(String hostname, int port, long timeoutMillis) throws IOException, TimeoutException  {
        return this.connect(new InetSocketAddress(hostname, port), timeoutMillis);
    }

    public TCPClient connect(String hostname, int port) throws IOException, TimeoutException  {
        return this.connect(hostname, port, 0L);
    }


    private void createConnection(SocketChannel channel) throws IOException {
        initialOptions.applyPostConnect(channel);

        final SelectionKey key = selectorLoop.registerReadKey(channel);

        connection = new TCPConnection(channel, key, connectionCodec, eventHandlers);
        connection.setName(this.makeConnectionName());

        initialOptions.copyTo(connection.options());

        connection.pushConnect();

        selectorLoop.startSelectionLoopThread(this.makeThreadName(), this::onKeySelected);
    }

    private String makeConnectionName() {
        return (CLASS_NAME + "-connection-#" + this.hashCode());
    }

    private String makeThreadName() {
        return (CLASS_NAME + "-selector-thread-#" + this.hashCode());
    }


    private void onKeySelected(SelectionKey key) {
        if(key.isReadable())
            connection.pushRead();
        if(key.isWritable())
            connection.pushSend();
    }


    public boolean isConnected() {
        return (connection != null && connection.isConnected());
    }

    public boolean isClosed() {
        return (connection == null || connection.isClosed());
    }

    public TCPClient close() {
        if(this.isClosed())
            return this;

        selectorLoop.close();
        connection.close(CloseReason.CLOSE_CLIENT, null);
        return this;
    }


    public TCPClient setEncryptCipher(Cipher encryptCipher) {
        if(connection == null)
            throw new IllegalStateException(CLASS_NAME + " is not connected");

        connection.ciphers().setEncryptCipher(encryptCipher);
        return this;
    }

    public TCPClient setDecryptCipher(Cipher decryptCipher) {
        if(connection == null)
            throw new IllegalStateException(CLASS_NAME + " is not connected");

        connection.ciphers().setDecryptCipher(decryptCipher);
        return this;
    }

    public TCPClient setCiphers(Cipher encryptCipher, Cipher decryptCipher) {
        if(connection == null)
            throw new IllegalStateException(CLASS_NAME + " is not connected");

        connection.ciphers().setCiphers(encryptCipher, decryptCipher);
        return this;
    }


    @Override
    public boolean send(byte[] byteArray) {
        if(connection == null)
            return false;
        return connection.send(byteArray);
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        if(connection == null)
            return false;
        return connection.send(buffer);
    }

    @Override
    public boolean send(String string) {
        if(connection == null)
            return false;
        return connection.send(string);
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        if(connection == null)
            return false;
        return connection.send(streamWriter);
    }

    @Override
    public boolean send(NetPacket<?> packet) {
        if(connection == null)
            return false;
        return connection.send(packet);
    }

}
