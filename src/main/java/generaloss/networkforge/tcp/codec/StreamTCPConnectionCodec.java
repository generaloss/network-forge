package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class StreamTCPConnectionCodec implements TCPConnectionCodec {

    private static final String CLASS_NAME = StreamTCPConnectionCodec.class.getSimpleName();
    private static final int DATA_BUFFER_SIZE = 8192; // 8 kb

    private TCPConnection connection;
    private final ByteBuffer dataBuffer;

    public StreamTCPConnectionCodec() {
        this.dataBuffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
    }

    @Override
    public void setup(TCPConnection connection) {
        this.connection = connection;
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
        final ByteBuffer buffer = ByteBuffer.allocate(size);
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
            // read all available data
            final ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();

            int length;
            while(true) {
                length = connection.readRaw(dataBuffer);
                if(length < 1)
                    break;

                // write buffer to bytesStream
                dataBuffer.flip();
                final byte[] chunk = new byte[length];
                dataBuffer.get(chunk);
                bytesStream.write(chunk);
                dataBuffer.clear();

                // check size
                if(bytesStream.size() > connection.options().getMaxReadFrameSize()) {
                    // close connection
                    if(connection.options().isCloseOnFrameSizeLimit())
                        connection.close(CloseReason.FRAME_SIZE_LIMIT_EXCEEDED, null);

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

            final byte[] allReadBytes = bytesStream.toByteArray();
            return connection.ciphers().decrypt(allReadBytes);

        } catch (IOException e) {
            connection.close(CloseReason.INTERNAL_ERROR, e);
            return null;
        }
    }

    private void discardAvailableBytes() throws IOException {
        dataBuffer.clear();
        while(true) {
            // read
            final int read = connection.readRaw(dataBuffer);
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
