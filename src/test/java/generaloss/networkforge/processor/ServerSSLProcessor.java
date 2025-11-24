package generaloss.networkforge.processor;

import generaloss.networkforge.packet.PacketDispatcher;
import generaloss.networkforge.processor.packet.Packet2CPublicKey;
import generaloss.networkforge.processor.packet.Packet2SEncryptedKey;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.TCPCloseReason;
import generaloss.networkforge.tcp.listener.TCPErrorSource;
import generaloss.networkforge.tcp.processor.TCPConnectionProcessor;
import generaloss.resourceflow.resource.Resource;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerSSLProcessor implements TCPConnectionProcessor {

    public static final int KEY_SIZE = 2048;

    private static class SSLTCPConnection {
        public SSLConnectionState state;
        public SecretKey secretKey;
    }

    private interface SSLServerPacketHandler {
        void onHandleEncryptedKey(TCPConnection connection, byte[] keyBytes);
    }

    private final Map<TCPConnection, SSLTCPConnection> sslConnectionMap;
    private final PacketDispatcher packetDispatcher;
    private final KeyPair keyPair;

    public ServerSSLProcessor(PacketDispatcher sharedPacketDispatcher) {
        this.sslConnectionMap = new ConcurrentHashMap<>();
        this.packetDispatcher = sharedPacketDispatcher;
        this.packetDispatcher.registerAll(
            Resource.classpath("generaloss/networkforge/processor/packet").listClasses()
        );

        try {
            final KeyPairGenerator pairGenerator = KeyPairGenerator.getInstance("RSA");
            pairGenerator.initialize(KEY_SIZE);
            keyPair = pairGenerator.generateKeyPair();

        } catch (NoSuchAlgorithmException ignored) {
            throw new RuntimeException("Failed to generate RSA key pair");
        }
    }

    public ServerSSLProcessor() {
        this(new PacketDispatcher());
    }

    @Override
    public boolean onConnect(TCPConnection connection) {
        final Packet2CPublicKey packet = new Packet2CPublicKey(keyPair.getPublic());
        final boolean sent = connection.send(packet);

        if(!sent)
            throw new RuntimeException("Failed to send public key]");

        sslConnectionMap.put(connection, new SSLTCPConnection());
        return false;
    }

    @Override
    public boolean onDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        sslConnectionMap.remove(connection);
        return true;
    }

    private volatile TCPConnection lastReceivedConnection;
    @Override
    public boolean onReceive(TCPConnection connection, byte[] byteArray) {
        final AtomicBoolean isSslPacket = new AtomicBoolean();

        packetDispatcher.dispatch(byteArray, packet -> {
            if(packet instanceof Packet2SEncryptedKey) {
                isSslPacket.set(true);
                lastReceivedConnection = connection;
                return this;
            }
            return null;
        });

        return !isSslPacket.get();
    }

    public void onHandleEncryptedKey(byte[] encryptedKeyBytes) {
        final SSLTCPConnection sslConnection = sslConnectionMap.get(lastReceivedConnection);
        if(sslConnection == null)
            return;

        try {
            final Cipher privateEncryptCipher = Cipher.getInstance("RSA");
            privateEncryptCipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());

            final byte[] keyBytes = privateEncryptCipher.doFinal(encryptedKeyBytes);

            sslConnection.secretKey = new SecretKeySpec(keyBytes, "AES");
            sslConnection.state = SSLConnectionState.GOT_SECRET_KEY;

            // try{
            //     final Cipher encryptCipher = Cipher.getInstance("RSA");
            //     encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            //     lastReceivedConnection.ciphers().setCiphers();
            // }catch(NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            //     throw new RuntimeException(e);
            // }

        } catch (GeneralSecurityException e){
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean onError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        return true;
    }

}