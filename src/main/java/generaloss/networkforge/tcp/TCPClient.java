package generaloss.networkforge.tcp;

import generaloss.networkforge.NetCloseCause;
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

    private Consumer<TCPConnection> onConnect;
    private TCPCloseable onClose;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;

    private TCPConnection connection;
    private Thread selectorThread;
    private Selector selector;

    public TCPClient() {
        this.setConnectionType(TCPConnectionType.DEFAULT);
        this.setOnError(TCPErrorHandler::printErrorCatch);
    }

    public TCPConnection connection() {
        return connection;
    }


    public TCPClient setConnectionType(Class<?> tcpConnectionClass) {
        this.connectionFactory = TCPConnection.getFactory(tcpConnectionClass);
        return this;
    }

    public TCPClient setConnectionType(TCPConnectionType connectionType) {
        this.connectionFactory = TCPConnection.getFactory(connectionType);
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

    public TCPClient setOnError(TCPErrorHandler onError) {
        this.onError = onError;
        return this;
    }


    private void invokeOnConnect(TCPConnection connection) {
        if(onConnect == null)
            return;

        try {
            onConnect.accept(connection);
        }catch(Exception onconnectException) {
            this.invokeOnError(connection, "onConnect callback", onconnectException);
        }
    }

    private void invokeOnDisconnect(TCPConnection connection, NetCloseCause netCloseCause, Exception e) {
        if(onClose == null)
            return;

        try {
            onClose.close(connection, netCloseCause, e);
        }catch(Exception oncloseException) {
            this.invokeOnError(connection, "onDisconnect callback", oncloseException);
        }
    }

    private void invokeOnReceive(TCPConnection connection, byte[] bytes) {
        if(onReceive == null || bytes == null)
            return;

        try {
            onReceive.receive(connection, bytes);
        }catch(Exception onreceiveException) {
            this.invokeOnError(connection, "onReceive callback", onreceiveException);
        }
    }

    private void invokeOnError(TCPConnection connection, String source, Exception exception) {
        try {
            onError.error(connection, source, exception);
        }catch(Exception onerrorException) {
            TCPErrorHandler.printErrorCatch(connection, "onError callback", exception);
        }
    }


    public TCPClient connect(SocketAddress socketAddress, long timeoutMillis) {
        if(this.isConnected())
            throw new AlreadyConnectedException();

        ResUtils.close(selector);

        try{
            // channel
            final SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
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
                throw new TimeoutException("Connection timed out");
            }

            // get key and connect
            final Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            final SelectionKey connectKey = keyIterator.next();
            keyIterator.remove();

            if(connectKey.isConnectable() && channel.finishConnect()) {
                this.createConnection(channel);
            }else{
                throw new ConnectException("Connection failed");
            }
        }catch(Exception e){
            ResUtils.close(selector);
            throw new RuntimeException("Failed to connect TCP client: ", e);
        }
        return this;
    }

    public TCPClient connect(SocketAddress socketAddress) {
        return this.connect(socketAddress, 0L);
    }

    public TCPClient connect(String hostname, int port, long timeoutMillis) {
        return this.connect(new InetSocketAddress(hostname, port), timeoutMillis);
    }

    public TCPClient connect(String hostname, int port) {
        return this.connect(hostname, port, 0L);
    }


    private void createConnection(SocketChannel channel) throws IOException {
        final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);

        connection = connectionFactory.create(channel, key, this::invokeOnDisconnect);
        connection.setName("TCPClient-connection #" + this.hashCode());

        this.invokeOnConnect(connection);
        this.startSelectorThread();
    }

    private void startSelectorThread() {
        selectorThread = new Thread(() -> {
            while(!Thread.interrupted() && !this.isClosed())
                this.selectKeys();
        }, "TCP client selector thread #" + this.hashCode());

        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    private void selectKeys() {
        try{
            if(selector.select() == 0)
                return;

            final Set<SelectionKey> selectedKeys = selector.selectedKeys();
            for(SelectionKey key: selectedKeys)
                this.processKey(key);
            selectedKeys.clear();
        }catch(Exception ignored) { }
    }

    private void processKey(SelectionKey key) {
        if(key.isValid() && key.isReadable()){
            final byte[] bytes = connection.read();
            this.invokeOnReceive(connection, bytes);
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

        connection.close(NetCloseCause.CLOSE_CLIENT, null);
        ResUtils.close(selector);
        return this;
    }


    public TCPClient encryptOutput(Cipher encryptCipher) {
        if(this.isConnected())
            connection.encrypter().encryptOutput(encryptCipher);
        return this;
    }

    public TCPClient encryptInput(Cipher decryptCipher) {
        if(this.isConnected())
            connection.encrypter().encryptInput(decryptCipher);
        return this;
    }

    public TCPClient encrypt(Cipher encryptCipher, Cipher decryptCipher) {
        if(this.isConnected())
            connection.encrypter().encrypt(encryptCipher, decryptCipher);
        return this;
    }


    public boolean send(ByteBuffer buffer) {
        if(connection != null)
            return connection.send(buffer);
        return false;
    }

    public boolean send(byte[] bytes) {
        if(connection != null)
            return connection.send(bytes);
        return false;
    }

    public boolean send(BinaryStreamWriter streamWriter) {
        if(connection != null)
            return connection.send(streamWriter);
        return false;
    }

    public boolean send(NetPacket<?> packet) {
        if(connection != null)
            return connection.send(packet);
        return false;
    }

}
