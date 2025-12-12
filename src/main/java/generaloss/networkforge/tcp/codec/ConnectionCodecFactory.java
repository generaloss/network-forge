package generaloss.networkforge.tcp.codec;

@FunctionalInterface
public interface ConnectionCodecFactory {

    ConnectionCodec create();

}
