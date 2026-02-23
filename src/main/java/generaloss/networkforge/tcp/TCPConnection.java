package generaloss.networkforge.tcp;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.codec.ByteStreamReader;
import generaloss.networkforge.tcp.codec.ByteStreamWriter;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.crypto.CipherPair;
import generaloss.networkforge.tcp.codec.ConnectionCodec;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.pipeline.EventPipeline;
import generaloss.networkforge.tcp.options.TCPConnectionOptions;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPConnection implements Sendable, Closeable {

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
            throw new IllegalArgumentException("Argument 'eventPipeline' cannot be null");

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

        if(this.codec != null)
            this.codec.setup(null, null, null);

        this.codec = codec;
    }

    public void setCodec(CodecType codecType) {
        if(codecType == null)
            throw new IllegalArgumentException("Argument 'codecType' cannot be null");

        final ConnectionCodec codec = codecType.getFactory().create();
        this.setCodec(codec);
    }

    public CipherPair getCiphers() {
        return ciphers;
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
        return (!channel.isConnected() || !channel.isOpen());
    }

    public void close(CloseReason reason, Exception e) {
        if(!channel.isOpen())
            return;

        key.cancel();
        ResUtils.close(channel);

        eventPipeline.fireDisconnect(this, reason, e);
    }

    @Override
    public void close() {
        this.close(CloseReason.CLOSE_CONNECTION, null);
    }

    protected void onConnectOp() {
        eventPipeline.fireConnect(this);
    }


    @Override
    public boolean send(byte[] data) {
        return eventPipeline.fireSend(this, data);
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        return eventPipeline.fireSend(this, buffer);
    }

    @Override
    public boolean send(String string) {
        return eventPipeline.fireSend(this, string);
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        return eventPipeline.fireSend(this, streamWriter);
    }

    @Override
    public boolean send(NetPacket<?> packet) {
        return eventPipeline.fireSend(this, packet);
    }


    public boolean sendDirect(byte[] data) {
        if(data == null || this.isClosed())
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
        eventPipeline.fireReceive(this, decryptedData);
    }

    private void writeOperationAvailable() {
        try {
            synchronized(writeLock) {
                final boolean queueFullyWritten = this.writeQueuedBuffers();
                if(queueFullyWritten) {
                    key.interestOpsAnd(~SelectionKey.OP_WRITE); // disable write operation
                    writeLock.notifyAll();
                }
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

    public void awaitWriteDrain(long timeoutMillis) throws InterruptedException {
        final long deadlineNanos = (System.nanoTime() + timeoutMillis * 1_000_000L);

        synchronized(writeLock) {
            while(true) {
                if(this.isClosed())
                    return;

                final boolean queueEmpty = sendQueue.isEmpty();
                final boolean writeDisabled = (key.interestOps() & SelectionKey.OP_WRITE) == 0;
                if(queueEmpty && writeDisabled)
                    return;

                final long remainingNanos = (deadlineNanos - System.nanoTime());
                if(remainingNanos <= 0L)
                    throw new IllegalStateException("Write drain timeout");

                long waitMillis = (remainingNanos / 1_000_000L);
                if(waitMillis == 0L)
                    waitMillis = 1L;

                writeLock.wait(waitMillis);
            }
        }
    }

    public int getPendingWriteCount() {
        return sendQueue.size();
    }

}
