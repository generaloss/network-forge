package generaloss.networkforge.tcp.codec;

public interface ConnectionCodec {

    /** @param data is non-null
     * @return false when cannot write data */
    boolean write(byte[] data);

    /** Called by selector only.
     * @return null when has no data available */
    byte[] read();

}
