package generaloss.networkforge.sslprocessor;

import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CConnectionEncrypted;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.TCPEventDispatcher;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

public class SSLTCPConnection {

    private final TCPEventDispatcher eventDispatcher;
    private final PrivateKey privateKey;
    private final TCPConnection connection;

    public SSLTCPConnection(TCPEventDispatcher eventDispatcher, TCPConnection connection, PrivateKey privateKey) {
        this.eventDispatcher = eventDispatcher;
        this.connection = connection;
        this.privateKey = privateKey;
    }

    public void onHandleEncryptedKey(byte[] encryptedKeyBytes) {
        try {
            final Cipher privateEncryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            privateEncryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            final byte[] keyBytes = privateEncryptCipher.doFinal(encryptedKeyBytes);

            try{
                // key
                final SecretKey key = new SecretKeySpec(keyBytes, "AES");

                // ciphers
                final Cipher encryptCipher = Cipher.getInstance("AES");
                encryptCipher.init(Cipher.ENCRYPT_MODE, key);

                final Cipher decryptCipher = Cipher.getInstance("AES");
                decryptCipher.init(Cipher.DECRYPT_MODE, key);

                connection.send(new Packet2CConnectionEncrypted());

                connection.ciphers().setCiphers(encryptCipher, decryptCipher);

                eventDispatcher.invokeOnConnectDirectly(connection);

            }catch(NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
                throw new RuntimeException(e);
            }

        } catch (GeneralSecurityException e){
            throw new RuntimeException(e);
        }
    }
}