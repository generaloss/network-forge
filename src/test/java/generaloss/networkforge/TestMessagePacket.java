package generaloss.networkforge;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.packet.PacketID;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

@PacketID(1)
public class TestMessagePacket extends NetPacket<TestPacketHandler> {

    private String message;

    public TestMessagePacket(String message) {
        this.message = message;
    }

    public TestMessagePacket() { }

    public void write(BinaryOutputStream stream) throws IOException {
        stream.writeByteString(message);
    }

    public void read(BinaryInputStream stream) throws IOException {
        message = stream.readByteString();
    }

    public void handle(TestPacketHandler handler) {
        handler.handleMessage(message);
    }
        
}