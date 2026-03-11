package generaloss.networkforge.packet;

public interface NetPacketFactory<P extends NetPacket> {

    P create() throws Exception;

}
