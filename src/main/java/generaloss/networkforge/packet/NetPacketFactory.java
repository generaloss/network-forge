package generaloss.networkforge.packet;

public interface NetPacketFactory<H> {

    NetPacket<H> create();

}
