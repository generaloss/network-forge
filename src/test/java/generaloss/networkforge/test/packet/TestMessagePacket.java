package generaloss.networkforge.test.packet;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.packet.PacketID;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

@PacketID(0)
public class TestMessagePacket extends NetPacket<TestPacketHandler> {

    private String message;

    public TestMessagePacket(String message) {
        this.message = message;
    }

    public TestMessagePacket() { }

    @Override
    protected void write(BinaryOutputStream stream) throws IOException {
        stream.writeByteString(message);
    }

    @Override
    protected void read(BinaryInputStream stream) throws IOException {
        message = stream.readByteString();
    }

    @Override
    public void handle(TestPacketHandler handler) {
        handler.handleMessage(message);
    }
        
}