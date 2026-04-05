package generaloss.networkforge.tcp.codec;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface ConnectionCodecFactory {

    ConnectionCodec create(TCPConnection connection, ByteStreamWriter writer, ByteStreamReader reader);

}
