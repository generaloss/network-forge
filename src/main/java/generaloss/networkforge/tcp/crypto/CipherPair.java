package generaloss.networkforge.tcp.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class CipherPair {

    private volatile Cipher encryptCipher;
    private volatile Cipher decryptCipher;
    private final Object encryptLock;
    private final Object decryptLock;

    public CipherPair() {
        this.encryptLock = new Object();
        this.decryptLock = new Object();
    }

    public Cipher getEncryptCipher() {
        return encryptCipher;
    }

    public void setEncryptCipher(Cipher encryptCipher) {
        this.encryptCipher = encryptCipher;
    }


    public Cipher getDecryptCipher() {
        return decryptCipher;
    }

    public void setDecryptCipher(Cipher decryptCipher) {
        this.decryptCipher = decryptCipher;
    }


    public void setCiphers(Cipher encryptCipher, Cipher decryptCipher) {
        this.setEncryptCipher(encryptCipher);
        this.setDecryptCipher(decryptCipher);
    }


    public byte[] encrypt(byte[] data) throws IllegalStateException {
        if(data == null)
            return null;

        final Cipher cipher = encryptCipher;
        if(cipher == null)
            return data;

        synchronized (encryptLock) {
            try {
                return cipher.doFinal(data);
            }catch(Exception e){
                throw new IllegalStateException("Encryption error", e);
            }
        }
    }

    public byte[] decrypt(byte[] data) throws IllegalStateException {
        if(data == null)
            return null;

        final Cipher cipher = decryptCipher;
        if(cipher == null)
            return data;

        synchronized (decryptLock) {
            try {
                return cipher.doFinal(data);
            }catch(IllegalBlockSizeException | BadPaddingException e){
                throw new IllegalStateException("Decryption error", e);
            }
        }
    }

    public int getEncryptedSize(int inputSize) {
        if(encryptCipher == null)
            return inputSize;

        synchronized (encryptLock) {
            return encryptCipher.getOutputSize(inputSize);
        }
    }

    public int getDecryptedSize(int inputSize) {
        if(decryptCipher == null)
            return inputSize;

        synchronized (decryptLock) {
            return decryptCipher.getOutputSize(inputSize);
        }
    }

}
