package generaloss.networkforge.sslprocessor.packet.c2s;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.sslprocessor.SSLTCPConnection;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;

public class Packet2SEncryptedKey extends NetPacket<SSLTCPConnection> {

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
    public void handle(SSLTCPConnection handler) {
        handler.onHandleEncryptedKey(encryptedKey);
    }

}