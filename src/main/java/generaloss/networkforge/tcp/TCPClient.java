package generaloss.networkforge.tcp;

import generaloss.networkforge.CipherPair;
import generaloss.networkforge.tcp.listener.*;
import generaloss.networkforge.tcp.options.TCPConnectionOptions;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryInputStream;
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
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class TCPClient {

    private TCPConnectionFactory connectionFactory;
    private TCPConnectionOptionsHolder initialOptions;

    private Consumer<TCPConnection> onConnect;
    private TCPCloseable onClose;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;

    private TCPConnection connection;
    private Thread selectorThread;
    private Selector selector;

    public TCPClient(TCPConnectionOptionsHolder initialOptions) {
        this.setConnectionType(TCPConnectionType.DEFAULT);
        this.setOnError((connection, source, throwable) ->
            TCPErrorHandler.printErrorCatch(TCPClient.class, connection, source, throwable)
        );
        this.initialOptions = initialOptions;
    }

    public TCPClient() {
        this(new TCPConnectionOptionsHolder());
    }


    public TCPConnection connection() {
        return connection;
    }

    public TCPConnectionOptions options() {
        if(connection == null)
            return null;
        return connection.options;
    }

    public CipherPair ciphers() {
        if(connection == null)
            return null;
        return connection.ciphers;
    }


    public TCPClient setConnectionType(Class<?> tcpConnectionClass) {
        this.connectionFactory = TCPConnection.getFactory(tcpConnectionClass);
        return this;
    }

    public TCPClient setConnectionType(TCPConnectionType connectionType) {
        this.connectionFactory = TCPConnection.getFactory(connectionType);
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


    public TCPClient setOnConnect(Consumer<TCPConnection> onConnect) {
        this.onConnect = onConnect;
        return this;
    }

    public TCPClient setOnDisconnect(TCPCloseable onClose) {
        this.onClose = onClose;
        return this;
    }

    public TCPClient setOnReceive(TCPReceiver onReceive) {
        this.onReceive = onReceive;
        return this;
    }

    public TCPClient setOnReceiveStream(TCPReceiverStream onReceive) {
        this.onReceive = (sender, byteArray) -> {
            final BinaryInputStream stream = new BinaryInputStream(byteArray);
            onReceive.receive(sender, stream);
            ResUtils.close(stream);
        };
        return this;
    }

    public TCPClient setOnError(TCPErrorHandler onError) {
        this.onError = onError;
        return this;
    }


    private void invokeOnConnect(TCPConnection connection) {
        if(onConnect == null)
            return;

        try {
            onConnect.accept(connection);
        }catch (Throwable onConnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.CONNECT_CALLBACK, onConnectThrowable);
        }
    }

    private void invokeOnDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        if(onClose == null)
            return;

        try {
            onClose.close(connection, reason, e);
        }catch (Throwable onCloseThrowable) {
            this.invokeOnError(connection, TCPErrorSource.DISCONNECT_CALLBACK, onCloseThrowable);
        }
    }

    private void invokeOnReceive(TCPConnection connection, byte[] byteArray) {
        if(onReceive == null || byteArray == null)
            return;

        try {
            onReceive.receive(connection, byteArray);
        }catch (Throwable onReceiveThrowable) {
            this.invokeOnError(connection, TCPErrorSource.RECEIVE_CALLBACK, onReceiveThrowable);
        }
    }

    private void invokeOnError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        try {
            onError.error(connection, source, throwable);
        }catch (Throwable onErrorThrowable) {
            TCPErrorHandler.printErrorCatch(TCPClient.class, connection, TCPErrorSource.ERROR_CALLBACK, onErrorThrowable);
        }
    }


    public TCPClient connect(SocketAddress socketAddress, long timeoutMillis) throws IOException, TimeoutException {
        if(this.isConnected())
            throw new AlreadyConnectedException();

        ResUtils.close(selector);

        // channel
        final SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        initialOptions.applyPreConnect(channel);

        final boolean connectedInstantly = channel.connect(socketAddress);
        selector = Selector.open();

        if(connectedInstantly) {
            this.createConnection(channel);
            return this;
        }

        // wait for connection
        channel.register(selector, SelectionKey.OP_CONNECT);
        if(selector.select(timeoutMillis) == 0) {
            channel.close();
            throw new TimeoutException("Connection timed out after " + timeoutMillis + " ms");
        }

        // get key and connect
        final Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
        final SelectionKey connectKey = keyIterator.next();
        keyIterator.remove();

        if(connectKey.isConnectable() && channel.finishConnect()) {
            this.createConnection(channel);
        }else{
            channel.close();
            throw new ConnectException("Connection failed");
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

        final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);

        connection = connectionFactory.create(channel, key, this::invokeOnDisconnect);
        connection.setName("TCPClient-connection-#" + this.hashCode());
        initialOptions.copyTo(connection.options());

        this.invokeOnConnect(connection);
        this.startSelectorThread();
    }

    private void startSelectorThread() {
        selectorThread = new Thread(() -> {
            while(!Thread.interrupted() && !this.isClosed())
                this.selectKeys();
        }, "TCPClient-selector-thread-#" + this.hashCode());

        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    private void selectKeys() {
        try{
            if(selector.select() == 0)
                return;

            final Set<SelectionKey> selectedKeys = selector.selectedKeys();
            for(SelectionKey key : selectedKeys)
                this.processKey(key);
            selectedKeys.clear();
        }catch (Exception ignored) { }
    }

    private void processKey(SelectionKey key) {
        if(key.isValid() && key.isReadable()){
            final byte[] byteArray = connection.read();
            this.invokeOnReceive(connection, byteArray);
        }
        if(key.isValid() && key.isWritable())
            connection.processWriteKey(key);
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

        if(selectorThread != null) {
            selectorThread.interrupt();
            selector.wakeup();
        }

        connection.close(TCPCloseReason.CLOSE_CLIENT, null);
        ResUtils.close(selector);
        return this;
    }


    public TCPClient setEncryptCipher(Cipher encryptCipher) {
        if(connection == null)
            throw new IllegalStateException("TCPClient is not connected");

        connection.ciphers().setEncryptCipher(encryptCipher);
        return this;
    }

    public TCPClient setDecryptCipher(Cipher decryptCipher) {
        if(connection == null)
            throw new IllegalStateException("TCPClient is not connected");

        connection.ciphers().setDecryptCipher(decryptCipher);
        return this;
    }

    public TCPClient setCiphers(Cipher encryptCipher, Cipher decryptCipher) {
        if(connection == null)
            throw new IllegalStateException("TCPClient is not connected");

        connection.ciphers().setCiphers(encryptCipher, decryptCipher);
        return this;
    }


    public boolean send(byte[] byteArray) {
        if(connection == null)
            return false;
        return connection.send(byteArray);
    }

    public boolean send(ByteBuffer buffer) {
        if(connection == null)
            return false;
        return connection.send(buffer);
    }

    public boolean send(String string) {
        if(connection == null)
            return false;
        return connection.send(string);
    }

    public boolean send(BinaryStreamWriter streamWriter) {
        if(connection == null)
            return false;
        return connection.send(streamWriter);
    }

    public boolean send(NetPacket<?> packet) {
        if(connection == null)
            return false;
        return connection.send(packet);
    }

}
