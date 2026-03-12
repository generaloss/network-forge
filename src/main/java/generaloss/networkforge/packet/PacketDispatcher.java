package generaloss.networkforge.packet;

import generaloss.networkforge.tcp.TCPConnection;

import java.util.List;
import java.util.concurrent.Executor;

public class PacketDispatcher {

    private static final int HANDLERS_BUFFER_SIZE = 65536; // short range

    private final NetPacketHandler<?>[] handlers;
    private Executor executor;

    public PacketDispatcher() {
        this.handlers = new NetPacketHandler[HANDLERS_BUFFER_SIZE]; // 512 KB
    }

    public final <P extends NetPacket> PacketDispatcher register(Class<P> packetClass, NetPacketHandler<P> handler) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");
        if(handler == null)
            throw new IllegalArgumentException("Argument 'handler' cannot be null");

        final short packetID = NetPacket.calculatePacketID(packetClass);
        final int index = (packetID & 0xFFFF);
        // check duplicate
        if(handlers[index] != null)
            throw new IllegalStateException("Handler already registered for net-packet '" + packetClass.getSimpleName() + "' (ID=" + packetID + ")");

        handlers[index] = handler;
        return this;
    }

    public PacketDispatcher async(Executor executor) {
        this.executor = executor;
        return this;
    }

    private void execute(Runnable runnable) {
        if(executor != null) {
            executor.execute(runnable);
        } else {
            runnable.run();
        }
    }


    @SuppressWarnings("unchecked")
    public <P extends NetPacket> NetPacketHandler<P> getHandler(int packetID) {
        final int index = (packetID & 0xFFFF); // unsigned short
        return (NetPacketHandler<P>) handlers[index];
    }

    public <P extends NetPacket> NetPacketHandler<P> getHandler(Class<?> packetClass) {
        if(packetClass == null)
            return null;

        final short packetID = NetPacket.calculatePacketID(packetClass);
        return this.getHandler(packetID);
    }

    public <P extends NetPacket> NetPacketHandler<P> getHandler(NetPacket packet) {
        if(packet == null)
            return null;

        final short packetID = packet.getPacketID();
        return this.getHandler(packetID);
    }


    public <P extends NetPacket> Runnable createHandleRunnable(TCPConnection connection, P packet) throws IllegalStateException {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        final NetPacketHandler<P> handler = this.getHandler(packet);
        if(handler == null)
            throw new IllegalStateException("Handler not registered for net-packet with ID=" + packet.getPacketID());

        // create handle task runnable
        return () -> handler.handle(connection, packet);
    }

    public <P extends NetPacket> Runnable createHandleRunnable(TCPConnection connection, P[] packets) {
        if(packets == null)
            throw new IllegalArgumentException("Argument 'packets' cannot be null");

        // create handle task runnable
        return () -> {
            for(P packet : packets) {
                if(packet == null)
                    continue;

                final NetPacketHandler<P> handler = this.getHandler(packet);
                if(handler != null)
                    handler.handle(connection, packet);
            }
        };
    }

    public <P extends NetPacket> Runnable createHandleRunnable(TCPConnection connection, List<P> packets) {
        if(packets == null)
            throw new IllegalArgumentException("Argument 'packets' cannot be null");

        // create handle task runnable
        return () -> {
            for(P packet : packets) {
                if(packet == null)
                    continue;

                final NetPacketHandler<P> handler = this.getHandler(packet);
                if(handler != null)
                    handler.handle(connection, packet);
            }
        };
    }

    public <P extends NetPacket> void dispatch(TCPConnection connection, P packet) throws IllegalStateException {
        final Runnable handleTask = this.createHandleRunnable(connection, packet);
        this.execute(handleTask);
    }

    public <P extends NetPacket> void dispatch(TCPConnection connection, P[] packets) throws IllegalStateException {
        final Runnable handleTask = this.createHandleRunnable(connection, packets);
        this.execute(handleTask);
    }

    public <P extends NetPacket> void dispatch(TCPConnection connection, List<P> packets) throws IllegalStateException {
        final Runnable handleTask = this.createHandleRunnable(connection, packets);
        this.execute(handleTask);
    }

}
