package generaloss.networkforge.tcp;

public enum TCPConnectionType {

    PACKET (PacketTCPConnection.class),
    STREAM (StreamTCPConnection.class);

    private final Class<?> conectionClass;

    TCPConnectionType(Class<?> conectionClass) {
        this.conectionClass = conectionClass;
    }

    public Class<?> getConnectionClass() {
        return conectionClass;
    }

    public static final TCPConnectionType DEFAULT = PACKET;

}
