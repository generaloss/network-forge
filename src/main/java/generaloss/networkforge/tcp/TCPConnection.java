package generaloss.networkforge.tcp;

import generaloss.networkforge.CipherPair;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;
import generaloss.networkforge.packet.NetPacket;

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
    protected final TCPConnectionOptions options;
    protected final CipherPair ciphers;
    private volatile boolean closed;
    private Object attachment;
    private String name;

    private final Queue<ByteBuffer> writeQueue;
    private final Object writeLock;

    public TCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        this.channel = channel;
        this.selectionKey = selectionKey;
        this.onClose = onClose;
        this.options = new TCPConnectionOptions(channel.socket());
        this.ciphers = new CipherPair();
        this.name = (this.getClass().getSimpleName() + "#" + this.hashCode());

        this.writeQueue = new ConcurrentLinkedQueue<>();
        this.writeLock = new Object();
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

    public TCPConnectionOptions options() {
        return options;
    }

    public CipherPair ciphers() {
        return ciphers;
    }


    @SuppressWarnings("unchecked")
    public <O> O attachment() {
        return (O) attachment;
    }

    public void attach(Object attachment) {
        this.attachment = attachment;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        if(name == null)
            throw new NullPointerException("Name is null");
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
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


    public boolean isConnected() {
        return (channel.isConnected() && !closed);
    }

    public boolean isClosed() {
        return closed;
    }

    protected void close(TCPCloseCause TCPCloseCause, Exception e) {
        if(closed)
            return;
        closed = true;

        if(onClose != null)
            onClose.close(this, TCPCloseCause, e);

        selectionKey.cancel();
        ResUtils.close(channel);
    }

    @Override
    public void close() {
        this.close(TCPCloseCause.CLOSE_CONNECTION, null);
    }


    protected boolean write(ByteBuffer buffer) {
        try {
            synchronized(writeLock) {
                // if first in queue
                if(writeQueue.isEmpty())
                    channel.write(buffer); // write now

                // if data not (fully) written
                if(buffer.hasRemaining()) {
                    // add to queue
                    writeQueue.add(buffer);
                    // enable write operation
                    selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
                    selectionKey.selector().wakeup();
                }
            }
            return true;
        }catch(IOException | CancelledKeyException e) {
            this.close(TCPCloseCause.INTERNAL_ERROR, e);
            return false;
        }
    }

    protected void processWriteKey(SelectionKey key) {
        try {
            synchronized(writeLock) {
                final boolean queueFullyWritten = this.tryToWriteQueuedData();
                if(!queueFullyWritten)
                    return; // continue writing next time

                // all queue written => disable write operation
                key.interestOps(SelectionKey.OP_READ);
            }
        }catch(Exception e) {
            this.close(TCPCloseCause.INTERNAL_ERROR, e);
        }
    }

    private boolean tryToWriteQueuedData() throws Exception {
        while(!writeQueue.isEmpty()) {
            final ByteBuffer buffer = writeQueue.peek();

            channel.write(buffer);

            // check is it can no longer write
            if(buffer.hasRemaining())
                return false;

            writeQueue.poll();
        }
        // all queue written
        return true;
    }


    protected abstract byte[] read();

    public abstract boolean send(byte[] bytes);


    public boolean send(ByteBuffer buffer) {
        if(this.isClosed())
            return false;

        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return this.send(bytes);
    }

    public boolean send(String str) {
        return this.send(str.getBytes());
    }

    public boolean send(BinaryStreamWriter streamWriter) {
        if(this.isClosed())
            return false;

        return this.send(BinaryStreamWriter.writeBytes(streamWriter));
    }

    public boolean send(NetPacket<?> packet) {
        if(this.isClosed())
            return false;

        return this.send(stream -> {
            stream.writeShort(packet.getPacketID());
            packet.write(stream);
        });
    }


    private static final Map<Class<?>, TCPConnectionFactory> FACTORY_BY_CLASS = new HashMap<>();

    public static void registerFactory(Class<?> connectionClass, TCPConnectionFactory factory) {
        FACTORY_BY_CLASS.put(connectionClass, factory);
    }

    static {
        registerFactory(PacketTCPConnection.class, PacketTCPConnection::new);
        registerFactory(StreamTCPConnection.class, StreamTCPConnection::new);
    }

    public static TCPConnectionFactory getFactory(Class<?> connectionClass) {
        if(!FACTORY_BY_CLASS.containsKey(connectionClass))
            throw new Error("Class '" + connectionClass + "' is not registered as a TCP connection factory");
        return FACTORY_BY_CLASS.get(connectionClass);
    }

    public static TCPConnectionFactory getFactory(TCPConnectionType connectionType) {
        return getFactory(connectionType.getConnectionClass());
    }

    public static TCPConnection create(Class<?> connectionClass, SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        final TCPConnectionFactory factory = getFactory(connectionClass);
        return factory.create(channel, selectionKey, onClose);
    }

}
