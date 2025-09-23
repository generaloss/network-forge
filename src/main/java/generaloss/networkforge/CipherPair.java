package generaloss.networkforge;

import javax.crypto.Cipher;

public class CipherPair {

    private Cipher encryptCipher;
    private Cipher decryptCipher;

    public void setEncryptCipher(Cipher encryptCipher) {
        this.encryptCipher = encryptCipher;
    }

    public void setDecryptCipher(Cipher decryptCipher) {
        this.decryptCipher = decryptCipher;
    }

    public void setCiphers(Cipher encryptCipher, Cipher decryptCipher) {
        this.setEncryptCipher(encryptCipher);
        this.setDecryptCipher(decryptCipher);
    }

    public synchronized byte[] encrypt(byte[] bytes) {
        if(encryptCipher == null)
            return bytes;
        if(bytes == null)
            return null;

        try {
            return encryptCipher.doFinal(bytes);
        }catch(Exception e) {
            throw new IllegalStateException("Encryption error: " + e.getMessage());
        }
    }

    public synchronized byte[] decrypt(byte[] bytes) {
        if(decryptCipher == null)
            return bytes;
        if(bytes == null)
            return null;

        try {
            return decryptCipher.doFinal(bytes);
        }catch(Exception e) {
            throw new IllegalStateException("Decryption error: " + e.getMessage());
        }
    }

    public synchronized int getEncryptedSize(int inputSize) {
        if(encryptCipher == null)
            return inputSize;
        return encryptCipher.getOutputSize(inputSize);
    }

    public synchronized int getDecryptedSize(int inputSize) {
        if(decryptCipher == null)
            return inputSize;
        return decryptCipher.getOutputSize(inputSize);
    }

}
