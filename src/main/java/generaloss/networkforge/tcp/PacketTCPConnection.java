package generaloss.networkforge.tcp;

import generaloss.networkforge.NetCloseCause;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class PacketTCPConnection extends TCPConnection {

    private final ByteBuffer lengthBuffer;
    private ByteBuffer dataBuffer;

    protected PacketTCPConnection(SocketChannel channel, SelectionKey selectionKey, TCPCloseable onClose) {
        super(channel, selectionKey, onClose);
        this.lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
    }

    @Override
    public boolean send(byte[] bytes) {
        if(super.isClosed())
            return false;

        bytes = super.encrypter.tryToEncryptBytes(bytes);

        final ByteBuffer buffer = ByteBuffer.allocate(4 + bytes.length);
        buffer.putInt(bytes.length); // length
        buffer.put(bytes); // data
        buffer.flip();
        return super.write(buffer);
    }

    @Override
    protected byte[] read() {
        try{
            // is needed to read length
            if(lengthBuffer.hasRemaining()) {
                // read length
                final boolean lengthFullyRead = this.readPartiallyTo(lengthBuffer);
                if(!lengthFullyRead)
                    return null; // continue reading length next time

                // allocate data buffer with length
                lengthBuffer.flip();
                final int length = lengthBuffer.getInt();
                dataBuffer = ByteBuffer.allocate(length);
            }

            // read data
            final boolean dataFullyRead = this.readPartiallyTo(dataBuffer);
            if(!dataFullyRead)
                return null; // continue reading data next time

            // reset length
            lengthBuffer.clear();
            // get data
            return this.getDecryptedData();

        }catch(IOException e) {
            super.close(NetCloseCause.INTERNAL_ERROR, e);
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
            super.close(NetCloseCause.CLOSE_BY_OTHER_SIDE, null);
            return false; // continue to read
        }

        // is fully read
        return (!buffer.hasRemaining());
    }

    private byte[] getDecryptedData() {
        dataBuffer.flip();
        final byte[] data = new byte[dataBuffer.remaining()];
        dataBuffer.get(data);

        return super.encrypter.tryToDecryptBytes(data);
    }

}
