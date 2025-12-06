package generaloss.networkforge;

import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.nio.ByteBuffer;

public interface ISendable {

    boolean send(byte[] byteArray);

    boolean send(ByteBuffer buffer);

    boolean send(String string);

    boolean send(BinaryStreamWriter streamWriter);

    boolean send(NetPacket<?> packet);

}
