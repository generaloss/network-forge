package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FramedTCPConnectionCodec implements TCPConnectionCodec {

    private static final String CLASS_NAME = FramedTCPConnectionCodec.class.getSimpleName();
    private static final int HEADER_SIZE = Integer.BYTES;
    private static final int DISCARD_BUFFER_SIZE = 8192; // 8 kb

    private TCPConnection connection;
    private final ByteBuffer sizeBuffer;
    private ByteBuffer dataBuffer;
    private int discardRemaining;

    public FramedTCPConnectionCodec() {
        this.sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
    }

    @Override
    public void setup(TCPConnection connection) {
        this.connection = connection;
        this.sizeBuffer.clear();
        this.discardRemaining = 0;
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");

        if(connection == null || connection.isClosed())
            return false;

        // encrypt data
        final byte[] contentData = connection.ciphers().encrypt(byteArray);

        // check data size
        final int contentSize = contentData.length;
        if(contentSize > connection.options().getMaxWriteFrameSize()) {
            System.err.printf("[%1$s] Frame to send is too large: %2$d bytes. Maximum allowed: %3$d bytes (adjustable).%n",
                    CLASS_NAME, contentSize, connection.options().getMaxWriteFrameSize()
            );
            return false;
        }

        // allocate buffer
        final int capacity = (HEADER_SIZE + contentSize);
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);

        buffer.putInt(contentSize);
        buffer.put(contentData);
        buffer.flip();

        // send
        return connection.sendRaw(buffer);
    }

    @Override
    public byte[] read() {
        if(connection == null)
            return null;

        try {
            // auxiliary loop
            while(true) {
                // if discard required
                if(discardRemaining > 0) {
                    if(!this.drainDiscardBytes())
                        return null; // continue reading/discarding next time
                }

                // if needed to read header
                if(sizeBuffer.hasRemaining()) {
                    // read header
                    final boolean sizeFullyRead = this.readPartiallyTo(sizeBuffer);
                    if(!sizeFullyRead)
                        return null; // continue reading header next time

                    // get data size
                    sizeBuffer.flip();
                    final int dataSize = sizeBuffer.getInt();

                    final int checkResult = this.checkContentSize(dataSize);
                    if(checkResult == -1) {
                        return null; // connection closed
                    }else if(checkResult == 1) {
                        // discard
                        if(this.drainDiscardBytes())
                            continue; // continue reading/discarding next time
                        return null;
                    }

                    // frame size is valid
                    this.setupDataBuffer(dataSize);
                }

                // if discard required
                if(discardRemaining > 0) {
                    if(this.drainDiscardBytes())
                        continue;
                    return null; // continue reading/discarding next time
                }

                // read data
                final boolean contentFullyRead = this.readPartiallyTo(dataBuffer);
                if(!contentFullyRead)
                    return null; // continue reading content data next time

                // prepare header buffer for next frame
                sizeBuffer.clear();
                // get content data
                return this.getDecryptedContentData();
            }

        } catch (IOException e) {
            connection.close(CloseReason.INTERNAL_ERROR, e);
            return null;
        }
    }

    private void setupDataBuffer(int contentSize) {
        final int frameBufferSizeUpperBound = connection.options().getFrameBufferSizeUpperBound();
        final boolean allocateBuffer = (
            dataBuffer == null || // allocate new
                contentSize > dataBuffer.capacity() // expand
        );
        final boolean dumpBuffer = (
            !allocateBuffer &&
                frameBufferSizeUpperBound != 0 &&
                contentSize > frameBufferSizeUpperBound
        );

        if(allocateBuffer || dumpBuffer) {
            // allocate frame data buffer
            dataBuffer = ByteBuffer.allocate(contentSize);
        } else {
            // set limit
            dataBuffer.clear();
            dataBuffer.limit(contentSize);
        }
    }

    /** @return -1 when closes connection; 1 when enables discard mode; 0 when frame size is valid */
    private int checkContentSize(int contentSize) throws IOException {
        // illegal content size - close connection
        if(contentSize < 1) {
            connection.close(CloseReason.INVALID_FRAME_SIZE, null);
            return -1;
        }

        // oversized frame handling
        if(contentSize > connection.options().getMaxReadFrameSize()) {
            // close connection if needed
            if(connection.options().isCloseOnFrameSizeLimit()) {
                connection.close(CloseReason.FRAME_SIZE_LIMIT_EXCEEDED, null);
                return -1;
            }

            // enter discard mode
            discardRemaining = contentSize;

            // reset header buffer for next frame
            sizeBuffer.clear();

            // setup content buffer for discarding
            if(dataBuffer == null || dataBuffer.capacity() < DISCARD_BUFFER_SIZE)
                dataBuffer = ByteBuffer.allocate(DISCARD_BUFFER_SIZE);

            return 1; // discard
        }
        return 0;
    }

    private boolean readPartiallyTo(ByteBuffer buffer) throws IOException {
        // check read necessity
        if(!buffer.hasRemaining())
            return true;

        // read bytes
        final int bytesRead = connection.readRaw(buffer);
        // check remote close
        if(bytesRead == -1){
            connection.close(CloseReason.CLOSE_BY_OTHER_SIDE, null);
            return false; // continue to read
        }

        // is fully read
        return !buffer.hasRemaining();
    }

    private byte[] getDecryptedContentData() {
        dataBuffer.flip();
        final byte[] data = new byte[dataBuffer.remaining()];
        dataBuffer.get(data);

        return connection.ciphers().decrypt(data);
    }

    private boolean drainDiscardBytes() throws IOException {
        dataBuffer.clear();
        while(true) {
            // limit
            final int toRead = Math.min(dataBuffer.capacity(), discardRemaining);
            dataBuffer.limit(toRead);
            // read
            final int read = connection.readRaw(dataBuffer);
            dataBuffer.clear();
            // check if no data
            if(read == 0)
                break;
            // check remote close
            if(read == -1) {
                connection.close(CloseReason.CLOSE_BY_OTHER_SIDE, null);
                break;
            }
            discardRemaining -= read;
        }
        return (discardRemaining == 0);
    }

}
