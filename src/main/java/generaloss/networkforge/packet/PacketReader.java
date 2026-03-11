package generaloss.networkforge.packet;

import generaloss.resourceflow.resource.ClasspathResource;
import generaloss.resourceflow.stream.BinaryInputStream;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PacketReader {

    private final Map<Short, NetPacketFactory<?>> factories;

    public PacketReader() {
        this.factories = new ConcurrentHashMap<>();
    }

    public final <P extends NetPacket> PacketReader register(Class<P> packetClass, NetPacketFactory<P> factory) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");
        if(factory == null)
            throw new IllegalArgumentException("Argument 'factory' cannot be null");

        // validate class
        if(!NetPacket.isConcretePacketClass(packetClass))
            throw new IllegalArgumentException("Cannot register abstract net-packet class '" + packetClass.getSimpleName() + "'");

        final short packetID = NetPacket.calculatePacketID(packetClass);
        // validate ID
        if(factories.containsKey(packetID))
            throw new IllegalStateException("Duplicate net-packet ID: " + packetID + " for '" + packetClass.getSimpleName() + "'");

        factories.put(packetID, factory);
        return this;
    }

    public final <P extends NetPacket> PacketReader register(Class<P> packetClass) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");

        // check for default constructor
        try {
            packetClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "Cannot register net-packet class '" + packetClass.getSimpleName() +
                    "': default (no-args) constructor not found"
            );
        }

        final NetPacketFactory<P> factory = (() -> NetPacket.createInstanceReflect(packetClass));
        return this.register(packetClass, factory);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final PacketReader registerAll(Class<? extends NetPacket>... packetClasses) {
        for(Class<? extends NetPacket> packetClass : packetClasses)
            this.register((Class<NetPacket>) packetClass);
        return this;
    }

    public final PacketReader registerAllFromPackage(ClasspathResource classpath) {
        final Class<NetPacket>[] packetClasses = classpath.listClasses(NetPacket::isConcretePacketClass);
        return this.registerAll(packetClasses);
    }

    public final PacketReader registerAllFromPackageRecursive(ClasspathResource classpath) {
        final Class<NetPacket>[] packetClasses = classpath.listClassesRecursive(NetPacket::isConcretePacketClass);
        return this.registerAll(packetClasses);
    }


    @SuppressWarnings("unchecked")
    public <P extends NetPacket> NetPacketFactory<P> getFactory(short packetID) {
        return (NetPacketFactory<P>) factories.get(packetID);
    }


    public <P extends NetPacket> P read(byte[] data) throws IOException, IllegalStateException {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        // check if data at least contains packetID
        if(data.length < Short.BYTES)
            throw new IllegalArgumentException("Size of 'data' is too small to read net-packet");

        final BinaryInputStream stream = new BinaryInputStream(data);
        try {
            final short packetID = stream.readShort();

            // create packet
            final NetPacketFactory<P> factory = this.getFactory(packetID);
            if(factory == null)
                throw new IllegalArgumentException("A net-packet with ID '" + packetID + "' not found");

            final P packet = factory.create();
            packet.read(stream);
            return packet;

        } catch (IOException e) {
            throw new IOException("Failed to read net-packet.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create net-packet object with factory.", e);
        }
    }

    public <P extends NetPacket> P readOrNull(byte[] data) {
        try {
            return this.read(data);
        } catch(IOException | IllegalStateException ignored) {
            return null;
        }
    }

    public <P extends NetPacket> Optional<P> tryRead(byte[] data) {
        try {
            final P packet = this.read(data);
            return Optional.of(packet);
        } catch(IOException | IllegalStateException ignored) {
            return Optional.empty();
        }
    }

}