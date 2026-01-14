package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.codec.ByteStreamReader;
import generaloss.networkforge.tcp.codec.ByteStreamWriter;
import generaloss.networkforge.tcp.crypto.CipherPair;
import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.handler.EventPipeline;
import generaloss.networkforge.tcp.options.TCPConnectionOptions;
import generaloss.resourceflow.ResUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPConnection implements DefaultSendable, Closeable {

    private static final String CLASS_NAME = TCPConnection.class.getSimpleName();

    private final SocketChannel channel;
    private final SelectionKey key;

    private ConnectionCodec codec;
    private final CipherPair ciphers;
    private final EventPipeline eventPipeline;
    private final TCPConnectionOptions options;

    private volatile Object attachment;
    private volatile String name;

    private final Queue<ByteBuffer> sendQueue;
    private final Object writeLock;

    public TCPConnection(SocketChannel channel, SelectionKey key, ConnectionCodec codec, EventPipeline eventPipeline) {
        if(channel == null)
            throw new IllegalArgumentException("Argument 'channel' cannot be null");
        if(key == null)
            throw new IllegalArgumentException("Argument 'key' cannot be null");
        if(eventPipeline == null)
            throw new IllegalArgumentException("Argument 'sharedEventHandlers' cannot be null");

        this.channel = channel;
        this.key = key;

        this.setCodec(codec);

        this.ciphers = new CipherPair();
        this.eventPipeline = eventPipeline;

        this.options = new TCPConnectionOptions(channel.socket());
        this.name = this.makeConnectionName();

        this.sendQueue = new ConcurrentLinkedQueue<>();
        this.writeLock = new Object();
    }

    private String makeConnectionName() {
        return (CLASS_NAME + "#" + this.hashCode());
    }


    public Socket getSocket() {
        return channel.socket();
    }

    public ConnectionCodec getCodec() {
        return codec;
    }

    public void setCodec(ConnectionCodec codec) {
        if(codec == null)
            throw new IllegalArgumentException("Argument 'codec' cannot be null");

        final ByteStreamWriter writer = this::onCodecWrite;
        final ByteStreamReader reader = channel::read;
        codec.setup(this, writer, reader);

        this.codec = codec;
    }

    public CipherPair getCiphers() {
        return ciphers;
    }

    public EventPipeline getEventPipeline() {
        return eventPipeline;
    }

    public TCPConnectionOptions getOptions() {
        return options;
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


    public int getPort() {
        return this.getSocket().getPort();
    }

    public int getLocalPort() {
        return this.getSocket().getLocalPort();
    }

    public InetAddress getAddress() {
        return this.getSocket().getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return this.getSocket().getLocalAddress();
    }


    public boolean isConnected() {
        return (channel.isConnected() && channel.isOpen());
    }

    public boolean isClosed() {
        return !this.isConnected();
    }

    public void close(CloseReason reason, Exception e) {
        if(!channel.isOpen())
            return;

        key.cancel();
        ResUtils.close(channel);

        eventPipeline.fireOnDisconnect(this, reason, e);
    }

    @Override
    public void close() {
        this.close(CloseReason.CLOSE_CONNECTION, null);
    }


    protected void onConnectOp() {
        eventPipeline.fireOnConnect(this);
    }

    @Override
    public boolean send(byte[] data) {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        final byte[] processedData = eventPipeline.fireOnSend(this, data);
        return this.sendDirect(processedData);
    }

    public boolean sendDirect(byte[] data) {
        if(data == null)
            return false;

        final byte[] encryptedData = ciphers.encrypt(data);
        return codec.write(encryptedData);
    }

    private void onCodecWrite(ByteBuffer buffer) throws IOException {
        synchronized(writeLock) {
            // if first in queue
            if(sendQueue.isEmpty())
                channel.write(buffer); // write now

            // if data not fully written
            if(buffer.hasRemaining()) {
                // add to queue
                sendQueue.add(buffer);
                // enable write op & wake up selector
                key.interestOpsOr(SelectionKey.OP_WRITE);
                key.selector().wakeup();
            }
        }
    }


    public void onKeySelected() {
        if(key.isReadable())
            this.readOperationAvailable();
        if(key.isWritable())
            this.writeOperationAvailable();
    }

    private void readOperationAvailable() {
        final byte[] data = codec.read();
        if(data == null)
            return;

        final byte[] decryptedData = ciphers.decrypt(data);
        eventPipeline.fireOnReceive(this, decryptedData);
    }

    private void writeOperationAvailable() {
        try {
            synchronized(writeLock) {
                final boolean queueFullyWritten = this.writeQueuedBuffers();
                if(queueFullyWritten)
                    key.interestOps(SelectionKey.OP_READ); // disable write operation
            }
        } catch (Exception e) {
            this.close(CloseReason.INTERNAL_ERROR, e);
        }
    }

    private boolean writeQueuedBuffers() throws Exception {
        while(!sendQueue.isEmpty()) {
            final ByteBuffer buffer = sendQueue.peek();

            channel.write(buffer);

            // check is it can no longer write
            if(buffer.hasRemaining())
                return false;

            sendQueue.poll();
        }
        // queue fully written
        return true;
    }

}
