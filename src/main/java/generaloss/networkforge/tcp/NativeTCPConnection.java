package generaloss.networkforge.tcp;

import generaloss.networkforge.NetCloseCause;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NativeTCPConnection extends TCPConnection {

    private static final int BUFFER_SIZE = 8192; // 8 kb

    private final ByteBuffer readBuffer;

    protected NativeTCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        super(channel, selectionKey, onClose);
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(super.isClosed())
            return false;

        // encrypt bytes
        final byte[] data = super.ciphers.encrypt(byteArray);

        // check size
        final int size = data.length;
        if(size > super.options.getMaxWritePacketSize()) {
            System.err.printf("[%1$s] Packet to send is too large: %2$d bytes. Maximum allowed: %3$d bytes (adjustable).%n",
                NativeTCPConnection.class.getSimpleName(), size, super.options.getMaxWritePacketSize()
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
                length = super.channel.read(readBuffer);
                if(length < 1)
                    break;

                // write buffer to bytesStream
                readBuffer.flip();
                final byte[] chunk = new byte[length];
                readBuffer.get(chunk);
                bytesStream.write(chunk);
                readBuffer.clear();

                // check size
                if(bytesStream.size() > super.options.getMaxReadPacketSize()) {
                    // close connection
                    if(super.options.isCloseOnPacketLimit())
                        super.close(NetCloseCause.PACKET_SIZE_LIMIT_EXCEEDED, null);

                    this.discardAvailableBytes();
                    return null;
                }
            }

            // check remote close
            if(length == -1) {
                super.close(NetCloseCause.CLOSE_BY_OTHER_SIDE, null);
                return null;
            }

            if(bytesStream.size() == 0)
                return null;

            final byte[] allReadBytes = bytesStream.toByteArray();
            return super.ciphers.decrypt(allReadBytes);

        }catch(IOException e) {
            super.close(NetCloseCause.INTERNAL_ERROR, e);
            return null;
        }
    }

    private void discardAvailableBytes() throws IOException {
        readBuffer.clear();
        while(true) {
            // read
            final int read = this.channel.read(readBuffer);
            readBuffer.clear();
            // check if no data
            if(read == 0)
                return;
            // check remote close
            if(read == -1) {
                this.close(NetCloseCause.CLOSE_BY_OTHER_SIDE, null);
                return;
            }
        }
    }

}
