package generaloss.networkforge.sslprocessor.packet.s2c;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.sslprocessor.ClientSSLProcessor;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

public class Packet2CConnectionEncrypted extends NetPacket<ClientSSLProcessor> {

    public Packet2CConnectionEncrypted() { }

    @Override
    protected void write(BinaryOutputStream output) { }

    @Override
    protected void read(BinaryInputStream input) { }

    @Override
    public void handle(ClientSSLProcessor handler) {
        handler.onReceiveEncryptedSignal();
    }

}
