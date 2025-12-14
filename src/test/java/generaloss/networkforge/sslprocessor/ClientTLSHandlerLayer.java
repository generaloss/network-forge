package generaloss.networkforge.sslprocessor;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.handler.EventHandlerLayer;
import generaloss.resourceflow.stream.BinaryInputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public class ClientTLSHandlerLayer extends EventHandlerLayer {

    public static final int AES_KEY_SIZE = 128;

    private volatile TCPConnection connection;
    private volatile SecretKey secretKey;
    private volatile boolean handshakeCompleted;
    private final ByteArrayOutputStream pendingData;

    public ClientTLSHandlerLayer() {
        this.pendingData = new ByteArrayOutputStream();
    }

    @Override
    public boolean handleConnect(TCPConnection connection) {
        this.connection = connection;
        return false;
    }

    @Override
    public boolean handleReceive(TCPConnection connection, byte[] byteArray) {
        if(handshakeCompleted)
            return true;

        try (final BinaryInputStream stream = new BinaryInputStream(byteArray)) {
            final int binaryFrame = stream.readByte();

            if(binaryFrame == TLSBinaryFrames.PUBLIC_KEY.ordinal()) {
                // PUBLIC_KEY
                final byte[] publicKeyBytes = stream.readByteArray();

                final PublicKey publicKey = KeyFactory
                    .getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));

                this.onReceivePublicKey(publicKey);
            }else if(binaryFrame == TLSBinaryFrames.CONNECTION_ENCRYPTED_SIGNAL.ordinal()) {
                // CONNECTION_ENCRYPTED_SIGNAL
                this.onReceiveEncryptedSignal();
            }else{
                // ?
                throw new RuntimeException("Invalid binary frame");
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read binary frame", e);
        }
        return false;
    }

    private void onReceivePublicKey(PublicKey publicKey) {
        try {
            // key
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_SIZE);
            secretKey = generator.generateKey();

            final Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

            final byte[] encryptedSecretKey = encryptCipher.doFinal(secretKey.getEncoded());
            this.sendEncryptedSecretKey(encryptedSecretKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendEncryptedSecretKey(byte[] encryptedSecretKey) {
        final boolean success = connection.send(stream -> {
            stream.writeByte(TLSBinaryFrames.ENCRYPTED_SECRET_KEY.ordinal());
            stream.writeByteArray(encryptedSecretKey);
        });
        if(!success)
            throw new RuntimeException("Failed to send encrypted secret key");
    }

    private void onReceiveEncryptedSignal() {
        try {
            // ciphers
            final Cipher encryptCipher = Cipher.getInstance("AES");
            encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);

            final Cipher decryptCipher = Cipher.getInstance("AES");
            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);

            connection.ciphers().setCiphers(encryptCipher, decryptCipher);

            handshakeCompleted = true;

            if(pendingData.size() > 0) {
                final byte[] bufferedData = pendingData.toByteArray();
                pendingData.reset();
                connection.send(bufferedData);
            }

            connection.eventHandlers().fireOnConnectNext(this, connection);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] handleSend(TCPConnection connection, byte[] byteArray) {
        if(handshakeCompleted)
            return super.handleSend(connection, byteArray);

        pendingData.writeBytes(byteArray);
        return null;
    }

}