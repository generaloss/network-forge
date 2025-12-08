package generaloss.networkforge.tcp.iohandler;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.TCPCloseReason;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FramedConnectionIOHandler implements ConnectionIOHandler {

    private static final int DISCARD_BUFFER_SIZE = 8192; // 8 kb

    private TCPConnection connection;
    private final ByteBuffer frameSizeBuffer;
    private ByteBuffer frameDataBuffer;
    private int bytesToDiscard; // discarding when > 0

    public FramedConnectionIOHandler() {
        this.frameSizeBuffer = ByteBuffer.allocate(Integer.BYTES);
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
                              FramedConnectionIOHandler.class.getSimpleName(), size, connection.options().getMaxFrameSizeWrite()
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
        return connection.writeRaw(buffer);
    }

    @Override
    public byte[] read() {
        if(connection == null)
            return null;

        try {
            // if discard required
            if(bytesToDiscard > 0) {
                this.processDiscard();
                return null; // continue discarding / return to normal mode
            }

            // is needed to read frame size
            if(frameSizeBuffer.hasRemaining()) {
                // read frame size
                final boolean sizeFullyRead = this.readPartiallyTo(frameSizeBuffer);
                if(!sizeFullyRead)
                    return null; // continue reading frame size next time

                // get frame size and validate
                frameSizeBuffer.flip();
                final int frameSize = frameSizeBuffer.getInt();

                if(!this.validateFrameSize(frameSize))
                    return null;

                // frame size is valid
                // allocate frame data buffer / set limit
                if(frameDataBuffer == null || frameSize > frameDataBuffer.capacity()) {
                    frameDataBuffer = ByteBuffer.allocate(frameSize);
                }else{
                    frameDataBuffer.clear();
                    frameDataBuffer.limit(frameSize);
                }
            }

            // if discard required
            if(bytesToDiscard > 0) {
                this.processDiscard();
                return null; // continue discarding / return to normal mode
            }

            // read data
            final boolean dataFullyRead = this.readPartiallyTo(frameDataBuffer);
            if(!dataFullyRead)
                return null; // continue reading data next time

            // reset size buffer for next frame
            frameSizeBuffer.clear();
            // get frame data
            return this.getDecryptedData();

        } catch (IOException e) {
            connection.close(TCPCloseReason.INTERNAL_ERROR, e);
            return null;
        }
    }

    private boolean validateFrameSize(int frameSize) throws IOException {
        // illegal frame size - close connection
        if(frameSize < 1) {
            connection.close(TCPCloseReason.INVALID_FRAME_SIZE, null);
            return false;
        }

        // oversized frame handling
        if(frameSize > connection.options().getMaxFrameSizeRead()) {
            // close connection if needed
            if(connection.options().isCloseOnFrameSizeLimit()) {
                connection.close(TCPCloseReason.FRAME_SIZE_LIMIT_EXCEEDED, null);
                return false;
            }

            // enter discard mode
            bytesToDiscard = frameSize;

            // reset size buffer for next frame
            frameSizeBuffer.clear();

            // setup data buffer for discarding
            if(frameDataBuffer == null || frameDataBuffer.capacity() < DISCARD_BUFFER_SIZE)
                frameDataBuffer = ByteBuffer.allocate(DISCARD_BUFFER_SIZE);

            // discard
            this.processDiscard();
            return false;
        }
        return true;
    }

    private boolean readPartiallyTo(ByteBuffer buffer) throws IOException {
        // check read necessity
        if(!buffer.hasRemaining())
            return true;

        // read bytes
        final int bytesRead = connection.channel().read(buffer);
        // check remote close
        if(bytesRead == -1){
            connection.close(TCPCloseReason.CLOSE_BY_OTHER_SIDE, null);
            return false; // continue to read
        }

        // is fully read
        return !buffer.hasRemaining();
    }

    private byte[] getDecryptedData() {
        frameDataBuffer.flip();
        final byte[] data = new byte[frameDataBuffer.remaining()];
        frameDataBuffer.get(data);

        return connection.ciphers().decrypt(data);
    }

    private void processDiscard() throws IOException {
        frameDataBuffer.clear();
        while(true) {
            // limit
            final int toRead = Math.min(frameDataBuffer.capacity(), bytesToDiscard);
            frameDataBuffer.limit(toRead);
            // read
            final int read = connection.channel().read(frameDataBuffer);
            frameDataBuffer.clear();
            // check if no data
            if(read == 0)
                break;
            // check remote close
            if(read == -1) {
                connection.close(TCPCloseReason.CLOSE_BY_OTHER_SIDE, null);
                break;
            }
            bytesToDiscard -= read;
        }
    }

}
