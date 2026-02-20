package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;

public interface ConnectionCodec {

    void setup(TCPConnection connection, ByteStreamWriter writer, ByteStreamReader reader);

    /** @param data is non-null
     * @return false when cannot write data */
    boolean write(byte[] data);

    /** Called by selector only.
     * @return null when has no data available */
    byte[] read();

}
