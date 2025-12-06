package generaloss.networkforge.tcp;

import generaloss.networkforge.tcp.listener.TCPCloseReason;
import generaloss.networkforge.tcp.listener.TCPCloseable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class FramedTCPConnection extends TCPConnection {

    private final ByteBuffer frameSizeBuffer;
    private ByteBuffer frameDataBuffer;
    private boolean discardReadingMode;

    protected FramedTCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        super(channel, selectionKey, onClose);
        this.frameSizeBuffer = ByteBuffer.allocate(Integer.BYTES);
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
                FramedTCPConnection.class.getSimpleName(), size, super.options.getMaxFrameSizeWrite()
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
        try {
            // is needed to read frame size
            if(frameSizeBuffer.hasRemaining() && !discardReadingMode) {
                // read frame size
                final boolean sizeFullyRead = this.readPartiallyTo(frameSizeBuffer);
                if(!sizeFullyRead)
                    return null; // continue reading frame size next time

                // get frame size
                frameSizeBuffer.flip();
                final int frameSize = frameSizeBuffer.getInt();

                if(frameSize < 1) {
                    super.close(TCPCloseReason.INVALID_FRAME_SIZE, null);
                    return null;
                }

                // check frame size
                if(frameSize > super.options.getMaxFrameSizeRead()) {
                    // close connection
                    if(super.options.isCloseOnFrameSizeLimit()) {
                        super.close(TCPCloseReason.FRAME_SIZE_LIMIT_EXCEEDED, null);
                        return null;
                    }

                    // enter discard mode, partially read all to {size}
                    discardReadingMode = true;
                    frameDataBuffer = ByteBuffer.allocate(frameSize);
                    this.readPartiallyTo(frameDataBuffer);

                    // reset size buffer for next frame
                    frameSizeBuffer.clear();
                    return null;
                }

                // allocate frame data buffer
                frameDataBuffer = ByteBuffer.allocate(frameSize);
            }

            // read data
            final boolean dataFullyRead = this.readPartiallyTo(frameDataBuffer);
            if(!dataFullyRead)
                return null; // continue reading data next time

            // all bytes to discard fully read => enter normal mode
            if(discardReadingMode) {
                discardReadingMode = false;
                return null;
            }

            // reset size buffer for next frame
            frameSizeBuffer.clear();
            // get frame data
            return this.getDecryptedData();

        } catch (IOException e) {
            super.close(TCPCloseReason.INTERNAL_ERROR, e);
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
            super.close(TCPCloseReason.CLOSE_BY_OTHER_SIDE, null);
            return false; // continue to read
        }

        // is fully read
        return (!buffer.hasRemaining());
    }

    private byte[] getDecryptedData() {
        frameDataBuffer.flip();
        final byte[] data = new byte[frameDataBuffer.remaining()];
        frameDataBuffer.get(data);

        return super.ciphers.decrypt(data);
    }

}
