package generaloss.networkforge.packet;

import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

public abstract class NetPacket<H> {

    private final short ID;

    public NetPacket() {
        this.ID = calculatePacketClassID(this.getClass());
    }

    public short getPacketID() {
        return ID;
    }

    abstract public void write(BinaryOutputStream stream) throws IOException;

    abstract public void read(BinaryInputStream stream) throws IOException;

    abstract public void handle(H handler);


    public static short calculatePacketClassID(Class<?> packetClass) {
        // check ID annotation
        final PacketID idAnnotation = packetClass.getAnnotation(PacketID.class);
        if(idAnnotation != null)
            return idAnnotation.value();

        // calculate with class name
        final String className = packetClass.getSimpleName();
        final int hash = className.hashCode();
        return (short) ((hash >>> 16) ^ hash);
    }

}
