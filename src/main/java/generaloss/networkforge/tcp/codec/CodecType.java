package generaloss.networkforge.tcp.codec;

public enum CodecType {

    FRAMED (FramedConnectionCodec::new),
    STREAM (StreamConnectionCodec::new);
    public static final CodecType DEFAULT = FRAMED;

    private final ConnectionCodecFactory factory;

    CodecType(ConnectionCodecFactory factory) {
        this.factory = factory;
    }

    public ConnectionCodecFactory getFactory() {
        return factory;
    }

}
