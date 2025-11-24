package generaloss.networkforge.sslprocessor;

import generaloss.networkforge.packet.PacketDispatcher;
import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CConnectionEncrypted;
import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CPublicKey;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.TCPCloseReason;
import generaloss.networkforge.tcp.listener.TCPErrorSource;
import generaloss.networkforge.tcp.processor.TCPConnectionProcessor;
import generaloss.resourceflow.resource.Resource;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientSSLProcessor implements TCPConnectionProcessor {

    public static final int AES_KEY_SIZE = 128;

    private final PacketDispatcher packetDispatcher;
    private TCPConnection connection;
    private SecretKey secretKey;

    public ClientSSLProcessor(PacketDispatcher sharedPacketDispatcher) {
        this.packetDispatcher = sharedPacketDispatcher;
        this.packetDispatcher.registerAll(
            Resource.classpath("generaloss/networkforge/sslprocessor/packet/s2c").listClasses()
        );
    }

    public ClientSSLProcessor() {
        this(new PacketDispatcher());
    }

    @Override
    public boolean onConnect(TCPConnection connection) {
        this.connection = connection;
        return false;
    }

    @Override
    public boolean onDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        return true;
    }

    @Override
    public boolean onReceive(TCPConnection connection, byte[] byteArray) {
        final AtomicBoolean isSslPacket = new AtomicBoolean();

        packetDispatcher.dispatch(byteArray, packet -> {
            if(packet instanceof Packet2CConnectionEncrypted || packet instanceof Packet2CPublicKey) {
                isSslPacket.set(true);
                return this;
            }
            return null;
        });

        return !isSslPacket.get();
    }

    @Override
    public boolean onError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        return true;
    }

    public void onReceivePublicKey(PublicKey publicKey) {
        try {
            // key
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_SIZE);
            secretKey = generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public void onReceiveEncryptedSignal() {
        try {
            // ciphers
            final Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);

            final Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);

            connection.ciphers().setCiphers(encryptCipher, decryptCipher);

            // TODO: onConnect
            System.out.println("client encrypted");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}