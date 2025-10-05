package generaloss.networkforge;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CryptoUtils {

    public static SecretKey generateSecretKey(int size) {
        try{
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(size);
            return generator.generateKey();
        }catch (NoSuchAlgorithmException ignored){
            return null;
        }
    }

    public static Cipher getEncryptCipher(SecretKey key) {
        try{
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher;

        }catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            throw new RuntimeException(e);
        }
    }

    public static Cipher getDecryptCipher(SecretKey key) {
        try{
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher;

        }catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            throw new RuntimeException(e);
        }
    }

}
