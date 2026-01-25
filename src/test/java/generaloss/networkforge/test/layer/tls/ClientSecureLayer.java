package generaloss.networkforge.test.layer.tls;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.pipeline.EventHandlerLayer;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.pipeline.EventPipelineContext;
import generaloss.resourceflow.stream.BinaryInputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public class ClientSecureLayer extends EventHandlerLayer {

    public static final int AES_KEY_SIZE = 128;

    private volatile TCPConnection connection;
    private volatile SecretKey secretKey;
    private volatile boolean handshakeCompleted;
    private final ByteArrayOutputStream pendingData;

    public ClientSecureLayer() {
        this.pendingData = new ByteArrayOutputStream();
    }

    @Override
    public boolean handleConnect(EventPipelineContext context) {
        this.connection = context.getConnection();
        return false;
    }


    public void handleDisconnect(EventPipelineContext context, CloseReason reason, Exception e) {
        handshakeCompleted = false;
    }

    @Override
    public boolean handleReceive(EventPipelineContext context, byte[] data) {
        if(handshakeCompleted)
            return true;

        try (final BinaryInputStream stream = new BinaryInputStream(data)) {
            final int binaryFrame = stream.readByte();

            if(binaryFrame == SecureBinaryFrames.PUBLIC_KEY.ordinal()) {
                // PUBLIC_KEY
                final byte[] publicKeyBytes = stream.readByteArray();

                final PublicKey publicKey = KeyFactory
                                                .getInstance("RSA")
                                                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));

                this.onReceivePublicKey(context, publicKey);
            }else if(binaryFrame == SecureBinaryFrames.CONNECTION_ENCRYPTED_SIGNAL.ordinal()) {
                // CONNECTION_ENCRYPTED_SIGNAL
                this.onReceiveEncryptedSignal(context);
            }else{
                // ?
                throw new RuntimeException("Invalid binary frame");
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read binary frame", e);
        }
        return false;
    }

    private void onReceivePublicKey(EventPipelineContext context, PublicKey publicKey) {
        try {
            // key
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_SIZE);
            secretKey = generator.generateKey();

            final Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

            final byte[] encryptedSecretKey = encryptCipher.doFinal(secretKey.getEncoded());
            this.sendEncryptedSecretKey(context, encryptedSecretKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendEncryptedSecretKey(EventPipelineContext context, byte[] encryptedSecretKey) {
        final boolean success = context.fireSend(stream -> {
            stream.writeByte(SecureBinaryFrames.ENCRYPTED_SECRET_KEY.ordinal());
            stream.writeByteArray(encryptedSecretKey);
        });
        if(!success)
            throw new RuntimeException("Failed to send encrypted secret key");
    }

    private void onReceiveEncryptedSignal(EventPipelineContext context) {
        try {
            // ciphers
            final Cipher encryptCipher = Cipher.getInstance("AES");
            encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);

            final Cipher decryptCipher = Cipher.getInstance("AES");
            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);

            connection.getCiphers().setCiphers(encryptCipher, decryptCipher);

            handshakeCompleted = true;

            if(pendingData.size() > 0) {
                final byte[] bufferedData = pendingData.toByteArray();
                pendingData.reset();
                connection.send(bufferedData);
            }

            context.fireOnConnect();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] handleSend(EventPipelineContext context, byte[] data) {
        if(handshakeCompleted)
            return data;

        pendingData.writeBytes(data);
        return null;
    }

}