package generaloss.networkforge.packet;

import generaloss.networkforge.tcp.TCPConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketDispatcher {

    private final Map<Class<?>, NetPacketHandler<?>> handlers;

    public PacketDispatcher() {
        this.handlers = new ConcurrentHashMap<>();
    }

    public final <P extends NetPacket> PacketDispatcher register(Class<P> packetClass, NetPacketHandler<P> handler) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");
        if(handler == null)
            throw new IllegalArgumentException("Argument 'handler' cannot be null");

        handlers.put(packetClass, handler);
        return this;
    }


    @SuppressWarnings("unchecked")
    public <P extends NetPacket> NetPacketHandler<P> getHandler(Class<?> packetClass) {
        return (NetPacketHandler<P>) handlers.get(packetClass);
    }


    public <P extends NetPacket> void dispatch(TCPConnection connection, P packet) {
        final NetPacketHandler<P> handler = this.getHandler(packet.getClass());
        handler.handle(connection, packet);
    }

}
