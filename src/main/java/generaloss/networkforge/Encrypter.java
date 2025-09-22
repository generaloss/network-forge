package generaloss.networkforge;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class Encrypter {

    private Cipher encryptCipher, decryptCipher;

    public void encryptOutput(Cipher encryptCipher) {
        this.encryptCipher = encryptCipher;
    }

    public void encryptInput(Cipher decryptCipher) {
        this.decryptCipher = decryptCipher;
    }

    public void encrypt(Cipher encryptCipher, Cipher decryptCipher) {
        this.encryptOutput(encryptCipher);
        this.encryptInput(decryptCipher);
    }

    public synchronized byte[] tryToEncryptBytes(byte[] bytes) {
        if(encryptCipher == null)
            return bytes;
        try {
            return encryptCipher.doFinal(bytes);
        }catch(IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("Encryption error: " + e.getMessage());
        }
    }

    public synchronized byte[] tryToDecryptBytes(byte[] bytes) {
        if(decryptCipher == null)
            return bytes;
        try {
            return decryptCipher.doFinal(bytes);
        }catch(IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("Decryption error: " + e.getMessage());
        }
    }

}
