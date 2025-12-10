package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;

public interface TCPConnectionCodec {

    void setup(TCPConnection connection);

    /** @return false when cannot send data */
    boolean send(byte[] byteArray);

    /** Called by selector.
     * @return null when has no data available */
    byte[] read();

}
