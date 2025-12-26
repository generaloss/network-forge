package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class StreamConnectionCodec implements ConnectionCodec {

    private static final String CLASS_NAME = StreamConnectionCodec.class.getSimpleName();
    private static final int DATA_BUFFER_SIZE = 8192; // 8 kb

    private TCPConnection connection;
    private ByteStreamWriter writer;
    private ByteStreamReader reader;

    private final ByteBuffer dataBuffer;

    public StreamConnectionCodec() {
        this.dataBuffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
    }

    @Override
    public void setup(TCPConnection connection, ByteStreamWriter writer, ByteStreamReader reader) {
        this.connection = connection;
        this.writer = writer;
        this.reader = reader;
    }

    public boolean write(byte[] data) {
        if(connection == null || connection.isClosed())
            return false;

        // check data size
        final int size = data.length;
        if(size == 0) {
            System.err.printf(
                "[%1$s %2$s] Data frame to send cannot be empty (length=0). ",
                connection.getName(), CLASS_NAME
            );
            return false;
        }

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
        final ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(data);
        buffer.flip();

        // write
        try {
            writer.write(buffer);
            return true;
        } catch (IOException e) {
            connection.close(CloseReason.INTERNAL_ERROR, e);
            return false;
        }
    }

    @Override
    public byte[] read() {
        if(connection == null)
            return null;

        try {
            // read all available data
            final ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();

            int length;
            while(true) {
                length = reader.read(dataBuffer);
                if(length < 1)
                    break;

                // write buffer to bytesStream
                dataBuffer.flip();
                final byte[] chunk = new byte[length];
                dataBuffer.get(chunk);
                bytesStream.write(chunk);
                dataBuffer.clear();

                // check size
                if(bytesStream.size() > connection.getOptions().getMaxReadFrameSize()) {
                    // close connection
                    if(connection.getOptions().isCloseOnFrameReadSizeExceed())
                        connection.close(CloseReason.FRAME_READ_SIZE_LIMIT_EXCEEDED, null);

                    this.discardAvailableBytes();
                    return null;
                }
            }

            // check remote close
            if(length == -1) {
                connection.close(CloseReason.CLOSE_BY_OTHER_SIDE, null);
                return null;
            }

            if(bytesStream.size() == 0)
                return null;

            return bytesStream.toByteArray();

        } catch (IOException e) {
            connection.close(CloseReason.INTERNAL_ERROR, e);
            return null;
        }
    }

    private void discardAvailableBytes() throws IOException {
        dataBuffer.clear();
        while(true) {
            // read
            final int read = reader.read(dataBuffer);
            dataBuffer.clear();
            // check if no data
            if(read == 0)
                return;
            // check remote close
            if(read == -1) {
                connection.close(CloseReason.CLOSE_BY_OTHER_SIDE, null);
                return;
            }
        }
    }

}
