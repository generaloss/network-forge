package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.listener.TCPCloseReason;
import generaloss.networkforge.tcp.listener.TCPCloseable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class StreamTCPConnection extends TCPConnection {

    private static final int BUFFER_SIZE = 8192; // 8 kb

    private final ByteBuffer readDataBuffer;

    protected StreamTCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        super(channel, selectionKey, onClose);
        this.readDataBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");

        if(super.isClosed())
            return false;

        // encrypt bytes
        final byte[] data = super.ciphers.encrypt(byteArray);

        // check size
        final int size = data.length;
        if(size > super.options.getMaxFrameSizeWrite()) {
            System.err.printf("[%1$s] Frame to send is too large: %2$d bytes. Maximum allowed: %3$d bytes (adjustable).%n",
                StreamTCPConnection.class.getSimpleName(), size, super.options.getMaxFrameSizeWrite()
            );
            return false;
        }

        // create buffer
        final ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(data);
        buffer.flip();

        // write buffer
        return super.write(buffer);
    }

    @Override
    protected byte[] read() {
        try {
            // read all available data
            final ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();

            int length;
            while(true) {
                length = super.channel.read(readDataBuffer);
                if(length < 1)
                    break;

                // write buffer to bytesStream
                readDataBuffer.flip();
                final byte[] chunk = new byte[length];
                readDataBuffer.get(chunk);
                bytesStream.write(chunk);
                readDataBuffer.clear();

                // check size
                if(bytesStream.size() > super.options.getMaxFrameSizeRead()) {
                    // close connection
                    if(super.options.isCloseOnFrameSizeLimit())
                        super.close(TCPCloseReason.FRAME_SIZE_LIMIT_EXCEEDED, null);

                    this.discardAvailableBytes();
                    return null;
                }
            }

            // check remote close
            if(length == -1) {
                super.close(TCPCloseReason.CLOSE_BY_OTHER_SIDE, null);
                return null;
            }

            if(bytesStream.size() == 0)
                return null;

            final byte[] allReadBytes = bytesStream.toByteArray();
            return super.ciphers.decrypt(allReadBytes);

        } catch (IOException e) {
            super.close(TCPCloseReason.INTERNAL_ERROR, e);
            return null;
        }
    }

    private void discardAvailableBytes() throws IOException {
        readDataBuffer.clear();
        while(true) {
            // read
            final int read = this.channel.read(readDataBuffer);
            readDataBuffer.clear();
            // check if no data
            if(read == 0)
                return;
            // check remote close
            if(read == -1) {
                this.close(TCPCloseReason.CLOSE_BY_OTHER_SIDE, null);
                return;
            }
        }
    }

}
