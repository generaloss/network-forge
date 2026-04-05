package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

/** Basically, this is state machine:
 * [Read header] → [Read data] → [Read header]
 *            ↓
 *      [Discard bytes]
 *            ↓
 *      [Read header]
 * */
public class FramedConnectionCodec implements ConnectionCodec {

    private static final String CLASS_NAME = FramedConnectionCodec.class.getSimpleName();
    private static final int HEADER_BUFFER_SIZE = Integer.BYTES; // 4 bytes for data size
    private static final int DISCARD_BUFFER_SIZE = 8192; // 8 kb

    private final TCPConnection connection;
    private final ByteStreamWriter writer;
    private final ByteStreamReader reader;

    private final ByteBuffer headerBuffer;
    private ByteBuffer dataBuffer;
    private ByteBuffer discardBuffer;
    private int discardRemaining;

    public FramedConnectionCodec(TCPConnection connection, ByteStreamWriter writer, ByteStreamReader reader) {
        this.connection = connection;
        this.writer = writer;
        this.reader = reader;

        this.headerBuffer = ByteBuffer.allocate(HEADER_BUFFER_SIZE);
    }

    @Override
    public boolean write(byte[] data) {
        if(connection.isClosed())
            return false;

        // check data size
        final int size = data.length;
        final int maxSize = connection.getOptions().getMaxWriteFrameSize();

        if(size > maxSize) {
            System.err.printf(
                "[%1$s %2$s] Frame to send is too large: %3$d bytes. " +
                "Maximum allowed: %4$d bytes (adjustable).%n",
                connection.getName(), CLASS_NAME, size, maxSize
            );
            return false;
        }

        // allocate buffer
        final int capacity = (HEADER_BUFFER_SIZE + size);
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);

        buffer.putInt(size);
        buffer.put(data);
        buffer.flip();

        // write
        try {
            writer.write(buffer);
            return true; // success

        } catch (ClosedChannelException | SocketException ignored) {
            return false;
        } catch (IOException e) {
            connection.getEventPipeline().fireError(connection, ErrorSource.SELECTOR_WRITE, e);
            connection.close(CloseReason.INTERNAL_ERROR);
            return false;
        }
    }

    @Override
    public synchronized byte[] read() {
        try {
            while(true) {
                // discard mode
                if(discardRemaining > 0) {
                    if(!this.discard())
                        return null; // continue reading/discarding next time

                    headerBuffer.clear(); // start read header
                }

                // read header
                if(headerBuffer.hasRemaining()) {
                    if(!this.readFully(headerBuffer))
                        return null; // continue reading header next time

                    // get data size
                    headerBuffer.flip();
                    final int size = headerBuffer.getInt();

                    // check data size
                    final int check = this.checkDataSize(size);
                    if(check == -1) // connection closed
                        return null;
                    if(check == 1) // discard
                        continue;

                    // 0 -> setup buffer
                    this.prepareDataBuffer(size);
                }

                // read data
                if(!this.readFully(dataBuffer))
                    return null; // continue reading data next time

                // get data
                dataBuffer.flip();
                final byte[] data = new byte[dataBuffer.remaining()];
                dataBuffer.get(data);
                dataBuffer.clear();

                headerBuffer.clear(); // start read header next time

                return data;
            }

        } catch (ClosedChannelException | SocketException ignored) {
            return null;
        } catch (IOException e) {
            connection.getEventPipeline().fireError(connection, ErrorSource.SELECTOR_READ, e);
            connection.close(CloseReason.INTERNAL_ERROR);
            return null;
        }
    }

    private void prepareDataBuffer(int size) {
        final int sizeUpperBound = connection.getOptions().getFrameBufferSizeUpperBound();

        final boolean allocateBuffer = (
                dataBuffer == null ||        // initialize buffer
                dataBuffer.capacity() < size // expand
        );
        final boolean reduceBufferSize = (
                !allocateBuffer &&      // buffer exists & no need to expand
                sizeUpperBound != 0 &&  // may be reduced
                (size > sizeUpperBound) // exceeds size bound
        );

        if(allocateBuffer || reduceBufferSize) {
            dataBuffer = ByteBuffer.allocate(size);
        } else {
            dataBuffer.clear();
            dataBuffer.limit(size);
        }
    }

    /** @return result code:
     * 0 when data size is valid;
     * -1 when closes the connection;
     * 1 when discard mode needs to be enabled. */
    private int checkDataSize(int size) throws IOException {
        // illegal data size received - close connection
        if(size < 0) {
            connection.close(CloseReason.INVALID_FRAME_SIZE);
            return -1;
        }

        // oversized frame handling
        final int max = connection.getOptions().getMaxReadFrameSize();

        if(size > max) {
            // close connection if needed
            if(connection.getOptions().isCloseOnFrameReadSizeExceed()) {
                connection.close(CloseReason.FRAME_READ_SIZE_LIMIT_EXCEEDED);
                return -1;
            }

            // enter discard mode
            discardRemaining = size;
            return 1; // discard
        }
        return 0;
    }

    private boolean readFully(ByteBuffer buffer) throws IOException {
        int readTotal = 0;
        while(buffer.hasRemaining()) {
            final int read = reader.read(buffer);
            readTotal += read;

            if(read == 0)
                break; // there's nothing to read

            if(read == -1) {
                connection.close(CloseReason.CLOSE_BY_OTHER_SIDE);
                break;
            }
        }

        return !buffer.hasRemaining(); // check is fully read
    }

    private boolean discard() throws IOException {
        if(discardBuffer == null)
            discardBuffer = ByteBuffer.allocate(DISCARD_BUFFER_SIZE);

        while(discardRemaining > 0) {
            discardBuffer.clear();

            final int toRead = Math.min(discardBuffer.capacity(), discardRemaining);
            discardBuffer.limit(toRead);

            final int read = reader.read(discardBuffer);
            if(read == 0)
                break; // there's nothing to discard

            if(read == -1) {
                connection.close(CloseReason.CLOSE_BY_OTHER_SIDE);
                break;
            }

            discardRemaining -= read;
        }

        return (discardRemaining == 0); // check is fully discard
    }

}
