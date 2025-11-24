package generaloss.networkforge.sslprocessor;

import generaloss.networkforge.packet.PacketDispatcher;
import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CPublicKey;
import generaloss.networkforge.sslprocessor.packet.c2s.Packet2SEncryptedKey;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.TCPCloseReason;
import generaloss.networkforge.tcp.listener.TCPErrorSource;
import generaloss.networkforge.tcp.processor.TCPConnectionProcessor;
import generaloss.resourceflow.resource.Resource;

import javax.crypto.*;
import java.security.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerSSLProcessor implements TCPConnectionProcessor {

    public static final int RSA_KEY_SIZE = 2048;

    private final Map<TCPConnection, SSLTCPConnection> sslConnectionMap;
    private final PacketDispatcher packetDispatcher;
    private final KeyPair keyPair;

    public ServerSSLProcessor(PacketDispatcher sharedPacketDispatcher) {
        this.sslConnectionMap = new ConcurrentHashMap<>();
        this.packetDispatcher = sharedPacketDispatcher;
        this.packetDispatcher.registerAll(
            Resource.classpath("generaloss/networkforge/sslprocessor/packet/c2s").listClasses()
        );

        try {
            final KeyPairGenerator pairGenerator = KeyPairGenerator.getInstance("RSA");
            pairGenerator.initialize(RSA_KEY_SIZE);
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

        final SSLTCPConnection sslConnection = new SSLTCPConnection(connection, keyPair.getPrivate());
        sslConnectionMap.put(connection, sslConnection);
        return false;
    }

    @Override
    public boolean onDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        sslConnectionMap.remove(connection);
        return true;
    }

    @Override
    public boolean onReceive(TCPConnection connection, byte[] byteArray) {
        final SSLTCPConnection sslConnection = sslConnectionMap.get(connection);
        if(sslConnection == null)
            return false;

        final AtomicBoolean isSslPacket = new AtomicBoolean();

        packetDispatcher.dispatch(byteArray, packet -> {
            if(packet instanceof Packet2SEncryptedKey) {
                isSslPacket.set(true);
                return sslConnection;
            }
            return null;
        });

        return !isSslPacket.get();
    }


    @Override
    public boolean onError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        return true;
    }

}