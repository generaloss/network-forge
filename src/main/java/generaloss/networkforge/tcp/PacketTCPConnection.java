package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.listener.TCPCloseCause;
import generaloss.networkforge.tcp.listener.TCPCloseable;
import generaloss.networkforge.tcp.options.TCPConnectionOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class PacketTCPConnection extends TCPConnection {

    public static final int DEFAULT_MAX_PACKET_SIZE = (8 * 1024 * 1024); // 8 Mb.  (Integer.MAX_VALUE ≈ 2 Gb)

    private final ByteBuffer sizeBuffer;
    private ByteBuffer dataBuffer;
    private boolean discardReading;

    protected PacketTCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose, TCPConnectionOptions options) {
        super(channel, selectionKey, onClose, options);
        this.sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(super.isClosed())
            return false;

        // encrypt bytes
        final byte[] data = super.ciphers.encrypt(byteArray);

        // check size
        final int size = data.length;
        if(size > super.options.getMaxPacketSizeWrite()) {
            System.err.printf("[%1$s] Packet to send is too large: %2$d bytes. Maximum allowed: %3$d bytes (adjustable).%n",
                PacketTCPConnection.class.getSimpleName(), size, super.options.getMaxPacketSizeWrite()
            );
            return false;
        }

        // create buffer
        final int capacity = (Integer.BYTES + size);
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);

        buffer.putInt(size); // size
        buffer.put(data); // data
        buffer.flip();

        // write buffer
        return super.write(buffer);
    }

    @Override
    protected byte[] read() {
        try{
            // is needed to read size
            if(sizeBuffer.hasRemaining() && !discardReading) {
                // read size
                final boolean sizeFullyRead = this.readPartiallyTo(sizeBuffer);
                if(!sizeFullyRead)
                    return null; // continue reading size next time

                // get size
                sizeBuffer.flip();
                final int size = sizeBuffer.getInt();

                // check size
                if(size > super.options.getMaxPacketSizeRead()) {
                    // close connection
                    if(super.options.isCloseOnPacketLimit()) {
                        super.close(TCPCloseCause.PACKET_SIZE_LIMIT_EXCEEDED, null);
                        return null;
                    }

                    // enter discard mode, partially read all to {size}
                    discardReading = true;
                    dataBuffer = ByteBuffer.allocate(size);
                    this.readPartiallyTo(dataBuffer);

                    // reset size buffer for next packet
                    sizeBuffer.clear();
                    return null;
                }

                // allocate data buffer
                dataBuffer = ByteBuffer.allocate(size);
            }

            // read data
            final boolean dataFullyRead = this.readPartiallyTo(dataBuffer);
            if(!dataFullyRead)
                return null; // continue reading data next time

            // all bytes to discard fully read => enter normal mode
            if(discardReading) {
                discardReading = false;
                return null;
            }

            // reset size buffer for next packet
            sizeBuffer.clear();
            // get data
            return this.getDecryptedData();

        }catch(IOException e) {
            super.close(TCPCloseCause.INTERNAL_ERROR, e);
            return null;
        }
    }

    private boolean readPartiallyTo(ByteBuffer buffer) throws IOException {
        // check read necessity
        if(!buffer.hasRemaining())
            return true;

        // read bytes
        final int bytesRead = super.channel.read(buffer);
        // check remote close
        if(bytesRead == -1){
            super.close(TCPCloseCause.CLOSE_BY_OTHER_SIDE, null);
            return false; // continue to read
        }

        // is fully read
        return (!buffer.hasRemaining());
    }

    private byte[] getDecryptedData() {
        dataBuffer.flip();
        final byte[] data = new byte[dataBuffer.remaining()];
        dataBuffer.get(data);

        return super.ciphers.decrypt(data);
    }

}
