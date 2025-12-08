package generaloss.networkforge.tcp.iohandler;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class StreamConnectionIOHandler implements ConnectionIOHandler {

    private static final int BUFFER_SIZE = 8192; // 8 kb

    private TCPConnection connection;
    private final ByteBuffer readDataBuffer;

    public StreamConnectionIOHandler() {
        this.readDataBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    @Override
    public void attach(TCPConnection connection) {
        this.connection = connection;
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");

        if(connection == null || connection.isClosed())
            return false;

        // encrypt bytes
        final byte[] data = connection.ciphers().encrypt(byteArray);

        // check size
        final int size = data.length;
        if(size > connection.options().getMaxFrameSizeWrite()) {
            System.err.printf("[%1$s] Frame to send is too large: %2$d bytes. Maximum allowed: %3$d bytes (adjustable).%n",
                              StreamConnectionIOHandler.class.getSimpleName(), size, connection.options().getMaxFrameSizeWrite()
            );
            return false;
        }

        // create buffer
        final ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(data);
        buffer.flip();

        // write buffer
        return connection.writeRaw(buffer);
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
                length = connection.channel().read(readDataBuffer);
                if(length < 1)
                    break;

                // write buffer to bytesStream
                readDataBuffer.flip();
                final byte[] chunk = new byte[length];
                readDataBuffer.get(chunk);
                bytesStream.write(chunk);
                readDataBuffer.clear();

                // check size
                if(bytesStream.size() > connection.options().getMaxFrameSizeRead()) {
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
        readDataBuffer.clear();
        while(true) {
            // read
            final int read = connection.channel().read(readDataBuffer);
            readDataBuffer.clear();
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
