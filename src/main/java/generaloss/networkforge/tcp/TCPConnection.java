package generaloss.networkforge.tcp;

import generaloss.networkforge.CipherPair;
import generaloss.networkforge.ISendable;
import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.event.CloseReason;
import generaloss.networkforge.tcp.event.CloseCallback;
import generaloss.networkforge.tcp.options.TCPConnectionOptions;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPConnection implements Closeable, ISendable {

    protected final SocketChannel channel;
    protected final SelectionKey selectionKey;
    protected final CloseCallback onClose;
    protected final TCPConnectionOptions options;
    protected final CipherPair ciphers;
    private volatile boolean closed;
    private volatile Object attachment;
    private volatile String name;
    
    private ConnectionCodec codec;
    private final Queue<ByteBuffer> sendQueue;
    private final Object writeLock;

    public TCPConnection(SocketChannel channel, SelectionKey selectionKey, CloseCallback onClose, ConnectionCodec codec) {
        if(channel == null)
            throw new IllegalArgumentException("Argument 'channel' cannot be null");
        if(selectionKey == null)
            throw new IllegalArgumentException("Argument 'selectionKey' cannot be null");
        if(onClose == null)
            throw new IllegalArgumentException("Argument 'onClose' cannot be null");

        this.channel = channel;
        this.selectionKey = selectionKey;
        this.onClose = onClose;
        this.options = new TCPConnectionOptions(channel.socket());
        this.ciphers = new CipherPair();
        this.name = (this.getClass().getSimpleName() + "#" + this.hashCode());

        this.setCodec(codec);
        this.sendQueue = new ConcurrentLinkedQueue<>();
        this.writeLock = new Object();
    }

    public Socket socket() {
        return channel.socket();
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
            throw new IllegalArgumentException("Argument 'name' cannot be null");

        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }


    public void setCodec(ConnectionCodec codec) {
        if(codec == null)
            throw new IllegalArgumentException("Argument 'codec' cannot be null");

        codec.setup(this);
        this.codec = codec;
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

    public void close(CloseReason reason, Exception e) {
        if(closed)
            return;
        closed = true;

        selectionKey.cancel();
        ResUtils.close(channel);

        onClose.onClose(this, reason, e);
    }

    @Override
    public void close() {
        this.close(CloseReason.CLOSE_CONNECTION, null);
    }


    public int readRaw(ByteBuffer buffer) throws IOException {
        return channel.read(buffer);
    }

    public boolean sendRaw(ByteBuffer buffer) {
        try {
            synchronized(writeLock) {
                // if first in queue
                if(sendQueue.isEmpty())
                    channel.write(buffer); // write now

                // if data not fully written
                if(buffer.hasRemaining()) {
                    // add to queue
                    sendQueue.add(buffer);
                    // enable write op & wake up selector
                    selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
                    selectionKey.selector().wakeup();
                }
            }
            return true;
        } catch (IOException | CancelledKeyException e) {
            this.close(CloseReason.INTERNAL_ERROR, e);
            return false;
        }
    }

    protected void pushSendQueue() {
        try {
            synchronized(writeLock) {
                final boolean queueFullyWritten = this.tryToSendQueuedBuffers();
                if(!queueFullyWritten)
                    return; // continue writing next time

                // queue fully written => disable write operation
                selectionKey.interestOps(SelectionKey.OP_READ);
            }
        } catch (Exception e) {
            this.close(CloseReason.INTERNAL_ERROR, e);
        }
    }

    private boolean tryToSendQueuedBuffers() throws Exception {
        while(!sendQueue.isEmpty()) {
            final ByteBuffer buffer = sendQueue.peek();

            channel.write(buffer);

            // check is it can no longer write
            if(buffer.hasRemaining())
                return false;

            sendQueue.poll();
        }
        // all queue written
        return true;
    }


    protected byte[] read() {
        return codec.read();
    }

    @Override
    public boolean send(byte[] byteArray) {
        return codec.send(byteArray);
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Agrument 'buffer' cannot be null");
        
        if(this.isClosed())
            return false;

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);
        return this.send(byteArray);
    }

    @Override
    public boolean send(String string) {
        if(string == null)
            throw new IllegalArgumentException("Agrument 'string' cannot be null");
        
        return this.send(string.getBytes());
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Agrument 'streamWriter' cannot be null");

        if(this.isClosed())
            return false;

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.send(byteArray);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public boolean send(NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Agrument 'packet' cannot be null");

        if(this.isClosed())
            return false;

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.send(byteArray);
        } catch (IOException ignored) {
            return false;
        }
    }

}
