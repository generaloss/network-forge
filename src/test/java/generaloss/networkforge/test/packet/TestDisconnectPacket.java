package generaloss.networkforge.test.packet;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.packet.PacketID;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

@PacketID(1)
public class TestDisconnectPacket extends NetPacket<TestPacketHandler> {

    private String reason;

    public TestDisconnectPacket(String reason) {
        this.reason = reason;
    }

    public TestDisconnectPacket() { }

    @Override
    protected void write(BinaryOutputStream stream) throws IOException {
        stream.writeByteString(reason);
    }

    @Override
    protected void read(BinaryInputStream stream) throws IOException {
        reason = stream.readByteString();
    }

    @Override
    public void handle(TestPacketHandler handler) {
        handler.handleDisconnect(reason);
    }

}
