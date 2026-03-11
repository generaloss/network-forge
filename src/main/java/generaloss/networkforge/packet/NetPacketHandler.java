package generaloss.networkforge.packet;

import generaloss.networkforge.tcp.TCPConnection;

public interface NetPacketHandler<P extends NetPacket> {

    void handle(TCPConnection connection, P packet);

}
