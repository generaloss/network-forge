package generaloss.networkforge.tcp.codec;

public enum CodecType {

    FRAMED (FramedTCPConnectionCodec::new),
    STREAM (StreamTCPConnectionCodec::new);

    private final TCPConnectionCodecFactory factory;

    CodecType(TCPConnectionCodecFactory factory) {
        this.factory = factory;
    }

    public TCPConnectionCodecFactory getFactory() {
        return factory;
    }

    public static final CodecType DEFAULT = FRAMED;

}
