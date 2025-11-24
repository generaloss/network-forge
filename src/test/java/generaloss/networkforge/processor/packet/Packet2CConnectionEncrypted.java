package generaloss.networkforge.processor.packet;

import generaloss.networkforge.packet.NetPacket;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

public class Packet2CConnectionEncrypted extends NetPacket<Runnable> {

    public Packet2CConnectionEncrypted() { }

    @Override
    protected void write(BinaryOutputStream output) { }

    @Override
    protected void read(BinaryInputStream input) { }

    @Override
    public void handle(Runnable handler) {
        handler.run();
    }

}
