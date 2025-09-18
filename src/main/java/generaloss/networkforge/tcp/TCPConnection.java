package generaloss.networkforge.tcp;

import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;
import generaloss.networkforge.packet.NetPacket;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class TCPConnection implements Closeable {

    protected final SocketChannel channel;
    protected final SelectionKey selectionKey;
    protected final TCPCloseable onClose;
    protected final TCPSocketOptions options;
    private volatile boolean closed;

    private Cipher encryptCipher, decryptCipher;
    private String name;
    private Object attachment;

    private final Queue<ByteBuffer> sendQueue;
    private final Object writeLock = new Object();

    public TCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        this.channel = channel;
        this.selectionKey = selectionKey;
        this.onClose = onClose;
        this.options = new TCPSocketOptions(channel.socket());
        this.sendQueue = new ConcurrentLinkedQueue<>();
        this.name = (this.getClass().getSimpleName() + "#" + this.hashCode());
    }

    public SocketChannel channel() {
        return channel;
    }

    public Socket socket() {
        return channel.socket();
    }

    public SelectionKey selectionKey() {
        return selectionKey;
    }

    public TCPSocketOptions options() {
        return options;
    }


    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if(name == null)
            throw new NullPointerException("Name is null");
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public <O> O attachment() {
        return (O) attachment;
    }

    public void attach(Object attachment) {
        this.attachment = attachment;
    }



    public void encryptOutput(Cipher encryptCipher) {
        this.encryptCipher = encryptCipher;
    }

    public void encryptInput(Cipher decryptCipher) {
        this.decryptCipher = decryptCipher;
    }

    public void encrypt(Cipher encryptCipher, Cipher decryptCipher) {
        this.encryptOutput(encryptCipher);
        this.encryptInput(decryptCipher);
    }

    protected synchronized byte[] tryToEncryptBytes(byte[] bytes) {
        if(encryptCipher == null)
            return bytes;
        try {
            return encryptCipher.doFinal(bytes);
        }catch(IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("Encryption error: " + e.getMessage());
        }
    }

    protected synchronized byte[] tryToDecryptBytes(byte[] bytes) {
        if(decryptCipher == null)
            return bytes;
        try {
            return decryptCipher.doFinal(bytes);
        }catch(IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("Decryption error: " + e.getMessage());
        }
    }


    protected abstract byte[] read();

    public abstract boolean send(byte[] bytes);

    public boolean send(BinaryStreamWriter streamWriter) {
        return this.send(BinaryStreamWriter.writeBytes(streamWriter));
    }

    public boolean send(NetPacket<?> packet) {
        return this.send(stream -> {
            stream.writeShort(packet.getPacketID());
            packet.write(stream);
        });
    }


    protected boolean toWriteQueue(ByteBuffer buffer) {
        try {
            synchronized(writeLock) {
                if(sendQueue.isEmpty())
                    channel.write(buffer);

                if(buffer.hasRemaining()) {
                    sendQueue.add(buffer);
                    selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
                    selectionKey.selector().wakeup();

                    this.processWriteQueue(selectionKey);
                }
            }
            return true;
        }catch(IOException | CancelledKeyException e) {
            this.close(e);
            return false;
        }
    }

    protected void processWriteQueue(SelectionKey key) {
        try {
            synchronized(writeLock) {
                while(!sendQueue.isEmpty()) {
                    final ByteBuffer sendBuffer = sendQueue.peek();
                    channel.write(sendBuffer);
                    if(sendBuffer.hasRemaining())
                        return;
                    sendQueue.poll();
                }
                key.interestOps(SelectionKey.OP_READ);
            }
        }catch(Exception e) {
            this.close(e);
        }
    }

    public int getSendQueueSize() {
        return sendQueue.size();
    }


    protected void close(String message) {
        if(closed)
            return;
        closed = true;

        if(onClose != null)
            onClose.close(this, message);

        selectionKey.cancel();
        ResUtils.close(channel);
    }

    protected void close(Exception e) {
        this.close("Error occurred. Close connection: " + e);
    }

    @Override
    public void close() {
        this.close("Connection closed");
    }


    public boolean isConnected() {
        return (channel.isConnected() && !closed);
    }

    public boolean isClosed() {
        return closed;
    }

    public int getPort() {
        return this.socket().getPort();
    }

    public int getLocalPort() {
        return this.socket().getLocalPort();
    }

    public InetAddress getAddress() {
        return this.socket().getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return this.socket().getLocalAddress();
    }


    public interface Factory {
        TCPConnection create(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose);
    }

    private static final Map<Class<?>, Factory> FACTORY_BY_CLASS = new HashMap<>();

    public static void registerFactory(Class<?> connectionClass, Factory factory) {
        FACTORY_BY_CLASS.put(connectionClass, factory);
    }

    static {
        registerFactory(PacketTCPConnection.class, PacketTCPConnection::new);
        registerFactory(NativeTCPConnection.class, NativeTCPConnection::new);
    }

    public static Factory getFactory(Class<?> connectionClass) {
        if(!FACTORY_BY_CLASS.containsKey(connectionClass))
            throw new Error("Class '" + connectionClass + "' is not registered as a TCP connection factory");
        return FACTORY_BY_CLASS.get(connectionClass);
    }

    public static Factory getFactory(TCPConnectionType connectionType) {
        return getFactory(connectionType.getConnectionClass());
    }

    public static TCPConnection create(Class<?> connectionClass, SocketChannel channel, SelectionKey selectionKey, TCPSocketOptions options, TCPCloseable onClose) {
        final Factory factory = getFactory(connectionClass);
        return factory.create(channel, selectionKey, onClose);
    }

}
