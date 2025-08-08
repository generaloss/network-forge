package generaloss.networkforge.packet;

import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

public abstract class NetPacket<H> {

    private final short ID;

    public NetPacket() {
        this.ID = getIDByClass(this.getClass());
    }

    public short getPacketID() {
        return ID;
    }

    abstract public void write(BinaryOutputStream stream) throws IOException;

    abstract public void read(BinaryInputStream stream) throws IOException;

    abstract public void handle(H handler);


    public static short getIDByClass(Class<?> c) {
        final int nameHash = c.getSimpleName().hashCode();
        return (short) (nameHash ^ (nameHash << 16));
    }

}
