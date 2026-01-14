package generaloss.networkforge.tcp;

import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.nio.ByteBuffer;

public interface Sendable {

    boolean send(byte[] data);

    boolean send(ByteBuffer buffer);

    boolean send(String string);

    boolean send(BinaryStreamWriter streamWriter);

    boolean send(NetPacket<?> packet);

}
