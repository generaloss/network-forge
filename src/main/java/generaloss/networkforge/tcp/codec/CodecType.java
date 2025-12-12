package generaloss.networkforge.tcp.codec;

public enum CodecType {

    FRAMED (FramedConnectionCodec::new),
    STREAM (StreamConnectionCodec::new);

    private final ConnectionCodecFactory factory;

    CodecType(ConnectionCodecFactory factory) {
        this.factory = factory;
    }

    public ConnectionCodecFactory getFactory() {
        return factory;
    }

    public static final CodecType DEFAULT = FRAMED;

}
