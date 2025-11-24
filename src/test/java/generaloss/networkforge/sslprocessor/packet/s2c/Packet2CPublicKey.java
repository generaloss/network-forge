package generaloss.networkforge.sslprocessor.packet.s2c;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.sslprocessor.ClientSSLProcessor;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class Packet2CPublicKey extends NetPacket<ClientSSLProcessor> {

    private PublicKey publicKey;

    public Packet2CPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Packet2CPublicKey() { }

    @Override
    protected void write(BinaryOutputStream output) throws IOException {
        output.writeByteArray(publicKey.getEncoded());
    }

    @Override
    protected void read(BinaryInputStream input) throws IOException {
        try {
            final byte[] keyBytes = input.readByteArray();

            publicKey = KeyFactory
                .getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));

        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handle(ClientSSLProcessor handler) {
        handler.onReceivePublicKey(publicKey);
    }

}
