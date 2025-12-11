package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Description.
 * */
public class FramedTCPConnectionCodec implements TCPConnectionCodec {

    private static final String CLASS_NAME = FramedTCPConnectionCodec.class.getSimpleName();
    private static final int HEADER_BUFFER_SIZE = Integer.BYTES; // 4 bytes for data size
    private static final int DISCARD_BUFFER_SIZE = 8192; // 8 kb

    private TCPConnection connection;
    private final ByteBuffer headerBuffer;
    private ByteBuffer dataBuffer;
    private int discardRemaining;

    public FramedTCPConnectionCodec() {
        this.headerBuffer = ByteBuffer.allocate(HEADER_BUFFER_SIZE);
    }

    @Override
    public void setup(TCPConnection connection) {
        this.connection = connection;
        this.headerBuffer.clear();
        this.discardRemaining = 0;
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");

        if(connection == null || connection.isClosed())
            return false;

        // encrypt data
        final byte[] data = connection.ciphers().encrypt(byteArray);

        // check data size
        final int size = data.length;

        final int maxSize = connection.options().getMaxWriteFrameSize();
        if(size > maxSize) {
            System.err.printf(
                "[%1$s] Frame to send is too large: %2$d bytes. " +
                "Maximum allowed: %3$d bytes (adjustable).%n",
                CLASS_NAME, size, maxSize
            );
            return false;
        }

        // allocate buffer
        final int capacity = (HEADER_BUFFER_SIZE + size);
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);

        buffer.putInt(size);
        buffer.put(data);
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
                if(headerBuffer.hasRemaining()) {
                    // read header
                    final boolean headerFullyRead = this.readPartiallyTo(headerBuffer);
                    if(!headerFullyRead)
                        return null; // continue reading header next time

                    // get data size
                    headerBuffer.flip();
                    final int dataSize = headerBuffer.getInt();

                    // check data size
                    final int checkResult = this.checkDataSize(dataSize);
                    if(checkResult == -1) {
                        // connection closed
                        return null;
                    } else if(checkResult == 1) {
                        // discard
                        if(this.drainDiscardBytes())
                            continue;
                        return null; // continue reading/discarding next time
                    }

                    // setup buffer
                    this.setupDataBuffer(dataSize);
                }

                // if discard required
                if(discardRemaining > 0) {
                    if(this.drainDiscardBytes())
                        continue;
                    return null; // continue reading/discarding next time
                }

                // read data
                final boolean dataFullyRead = this.readPartiallyTo(dataBuffer);
                if(!dataFullyRead)
                    return null; // continue reading data next time

                // prepare header buffer for next frame
                headerBuffer.clear();
                // get data
                return this.getDecryptedData();
            }

        } catch (IOException e) {
            connection.close(CloseReason.INTERNAL_ERROR, e);
            return null;
        }
    }

    private void setupDataBuffer(int size) {
        final int sizeUpperBound = connection.options().getFrameBufferSizeUpperBound();

        final boolean allocateBuffer = (
                dataBuffer == null || // allocate new
                size > dataBuffer.capacity() // expand
        );
        final boolean reduceBufferSize = (
                !allocateBuffer && // buffer exists & bigger than required
                sizeUpperBound != 0 && // can be reduced
                size > sizeUpperBound // exceeds size bound
        );

        if(allocateBuffer || reduceBufferSize) {
            // allocate
            dataBuffer = ByteBuffer.allocate(size);
        } else {
            // set limit
            dataBuffer.clear();
            dataBuffer.limit(size);
        }
    }

    /** @return
     * 0 when data size is valid;
     * -1 when closes the connection;
     * 1 when discard mode needs to be enabled. */
    private int checkDataSize(int size) throws IOException {
        // illegal data size received - close connection
        if(size < 1) {
            connection.close(CloseReason.INVALID_FRAME_SIZE, null);
            return -1;
        }

        // oversized frame handling
        final int maxSize = connection.options().getMaxReadFrameSize();
        if(size > maxSize) {
            // close connection if needed
            if(connection.options().isCloseOnFrameSizeLimit()) {
                connection.close(CloseReason.FRAME_SIZE_LIMIT_EXCEEDED, null);
                return -1;
            }

            // enter discard mode
            discardRemaining = size;

            // reset header buffer for next frame
            headerBuffer.clear();

            // setup data buffer for discarding
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

    private byte[] getDecryptedData() {
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
