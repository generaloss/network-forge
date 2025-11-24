package generaloss.networkforge.sslprocessor;

import generaloss.networkforge.sslprocessor.packet.s2c.Packet2CConnectionEncrypted;
import generaloss.networkforge.tcp.TCPConnection;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

public class SSLTCPConnection {

    private final PrivateKey privateKey;
    private final TCPConnection connection;

    public SSLTCPConnection(TCPConnection connection, PrivateKey privateKey) {
        this.connection = connection;
        this.privateKey = privateKey;
    }

    public void onHandleEncryptedKey(byte[] encryptedKeyBytes) {
        try {
            final Cipher privateEncryptCipher = Cipher.getInstance("RSA");
            privateEncryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            final byte[] keyBytes = privateEncryptCipher.doFinal(encryptedKeyBytes);

            try{
                // key
                final SecretKey key = new SecretKeySpec(keyBytes, "AES");

                // ciphers
                final Cipher encryptCipher = Cipher.getInstance("RSA");
                encryptCipher.init(Cipher.ENCRYPT_MODE, key);

                final Cipher decryptCipher = Cipher.getInstance("RSA");
                decryptCipher.init(Cipher.DECRYPT_MODE, key);

                connection.send(new Packet2CConnectionEncrypted());

                connection.ciphers().setCiphers(encryptCipher, decryptCipher);

                System.out.println("server client encrypted");

            }catch(NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
                throw new RuntimeException(e);
            }

        } catch (GeneralSecurityException e){
            throw new RuntimeException(e);
        }
    }
}