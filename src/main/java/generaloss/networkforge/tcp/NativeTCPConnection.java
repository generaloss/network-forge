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
    public boolean send(byte[] bytes) {
        if(super.isClosed())
            return false;

        bytes = super.encrypter.tryToEncryptBytes(bytes);

        final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();
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
            }

            if(length == -1) {
                super.close(NetCloseCause.CLOSE_BY_OTHER_SIDE, null);
                return null;
            }

            if(bytesStream.size() == 0)
                return null;

            final byte[] allReadBytes = bytesStream.toByteArray();
            return super.encrypter.tryToDecryptBytes(allReadBytes);

        }catch(IOException e) {
            super.close(NetCloseCause.INTERNAL_ERROR, e);
            return null;
        }
    }

}
