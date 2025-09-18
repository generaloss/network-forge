package generaloss.networkforge.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class PacketTCPConnection extends TCPConnection {

    private final ByteBuffer lengthBuffer;
    private ByteBuffer dataBuffer;

    protected PacketTCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        super(channel, selectionKey, onClose);
        this.lengthBuffer = ByteBuffer.allocate(4);
    }

    @Override
    protected byte[] read(boolean control) {
        try{
            // read data length first
            final boolean lengthFullyRead = this.tryToReadDataLength();
            if(!lengthFullyRead)
                return null; // continue to read length

            return this.readData(control);

        }catch(IOException e) {
            this.close(e);
            return null;
        }
    }

    private boolean tryToReadDataLength() throws IOException {
        // check read length necessity
        if(!lengthBuffer.hasRemaining())
            return true;

        // read bytes
        final int bytesRead = super.channel.read(lengthBuffer);

        // check is connection closed
        if(bytesRead == -1){
            this.close("Connection closed on other side");
            return false; // continue to read length
        }

        // length reading is not complete
        if(lengthBuffer.hasRemaining())
            return false; // continue to read length

        // allocate new data buffer with read length
        lengthBuffer.flip();
        final int length = lengthBuffer.getInt();
        dataBuffer = ByteBuffer.allocate(length);
        return true;
    }

    private byte[] readData(boolean control) throws IOException {
        // read data
        final int bytesRead = super.channel.read(dataBuffer);
        if(bytesRead == -1) {
            // connection closed
            this.close("Connection closed on other side");
            return null;
        }

        if(control) // disconnect on other side just checked and here control is done.
            return null;

        // data reading is not complete
        if(dataBuffer.hasRemaining())
            return null;

        // reset length buffer for next message
        lengthBuffer.clear();
        // process the message
        dataBuffer.flip();
        // decrypt
        final byte[] data = new byte[dataBuffer.remaining()];
        dataBuffer.get(data);
        return super.tryToDecryptBytes(data);
    }


    @Override
    public boolean send(byte[] bytes) {
        if(super.isClosed())
            return false;

        bytes = this.tryToEncryptBytes(bytes);

        final ByteBuffer buffer = ByteBuffer.allocate(4 + bytes.length);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return super.toWriteQueue(buffer);
    }

}
