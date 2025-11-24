package generaloss.networkforge.processor.packet;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.processor.ServerSSLProcessor;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

public class Packet2SEncryptedKey extends NetPacket<ServerSSLProcessor> {

    private byte[] encryptedKey;

    public Packet2SEncryptedKey(byte[] encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public Packet2SEncryptedKey() { }

    @Override
    protected void write(BinaryOutputStream output) throws IOException {
        output.writeByteArray(encryptedKey);
    }

    @Override
    protected void read(BinaryInputStream input) throws IOException {
        encryptedKey = input.readByteArray();
    }

    @Override
    public void handle(ServerSSLProcessor handler) {
        handler.onHandleEncryptedKey(encryptedKey);
    }

}