package generaloss.networkforge.sslprocessor;

import generaloss.networkforge.packet.PacketDispatcher;
import generaloss.networkforge.sslprocessor.packet.c2s.Packet2SEncryptedKey;
import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CConnectionEncrypted;
import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CPublicKey;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;
import generaloss.networkforge.tcp.event.ErrorSource;
import generaloss.networkforge.tcp.event.EventDispatcher;
import generaloss.networkforge.tcp.processor.TCPProcessor;
import generaloss.resourceflow.resource.Resource;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientSSLProcessor implements TCPProcessor {

    public static final int AES_KEY_SIZE = 128;

    private final PacketDispatcher packetDispatcher;
    private EventDispatcher eventDispatcher;
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
    public void onAdded(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public boolean onConnect(TCPConnection connection) {
        this.connection = connection;
        return false;
    }

    @Override
    public boolean onDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
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
    public boolean onError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        return true;
    }

    public void onReceivePublicKey(PublicKey publicKey) {
        try {
            // key
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_SIZE);
            secretKey = generator.generateKey();

            final Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            final byte[] encryptedAesKey = encryptCipher.doFinal(secretKey.getEncoded());
            connection.send(new Packet2SEncryptedKey(encryptedAesKey));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public void onReceiveEncryptedSignal() {
        try {
            // ciphers
            final Cipher encryptCipher = Cipher.getInstance("AES");
            encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);

            final Cipher decryptCipher = Cipher.getInstance("AES");
            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);

            connection.ciphers().setCiphers(encryptCipher, decryptCipher);

            eventDispatcher.invokeOnConnectDirectly(connection);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}