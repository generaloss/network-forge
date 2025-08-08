package generaloss.networkforge.tcp;

public enum TCPConnectionType {

    NATIVE (NativeTCPConnection.class),
    PACKET (PacketTCPConnection.class);

    private final Class<?> conectionClass;

    TCPConnectionType(Class<?> conectionClass) {
        this.conectionClass = conectionClass;
    }

    public Class<?> getConnectionClass() {
        return conectionClass;
    }

    static final TCPConnectionType DEFAULT = PACKET;

}
