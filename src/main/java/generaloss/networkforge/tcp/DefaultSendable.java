package generaloss.networkforge.tcp;

import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface DefaultSendable extends Sendable {

    boolean isClosed();

    @Override
    default boolean send(ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Agrument 'buffer' cannot be null");

        if(this.isClosed())
            return false;

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);
        return this.send(byteArray);
    }

    @Override
    default boolean send(String string) {
        if(string == null)
            throw new IllegalArgumentException("Agrument 'string' cannot be null");

        return this.send(string.getBytes());
    }

    @Override
    default boolean send(BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Agrument 'streamWriter' cannot be null");

        if(this.isClosed())
            return false;

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.send(byteArray);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    default boolean send(NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Agrument 'packet' cannot be null");

        if(this.isClosed())
            return false;

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.send(byteArray);
        } catch (IOException ignored) {
            return false;
        }
    }

}
