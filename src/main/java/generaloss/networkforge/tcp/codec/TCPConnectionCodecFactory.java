package generaloss.networkforge.tcp.codec;

@FunctionalInterface
public interface TCPConnectionCodecFactory {

    TCPConnectionCodec create();

}
