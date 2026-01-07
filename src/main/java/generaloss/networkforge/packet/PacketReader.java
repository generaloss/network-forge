package generaloss.networkforge.packet;

import generaloss.resourceflow.resource.ClasspathResource;
import generaloss.resourceflow.stream.BinaryInputStream;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PacketReader {

    private final Map<Short, NetPacketFactory<?>> packetFactories;

    public PacketReader() {
        this.packetFactories = new ConcurrentHashMap<>();
    }

    public final <H, P extends NetPacket<H>> PacketReader register(Class<P> packetClass, NetPacketFactory<H> factory) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");
        if(factory == null)
            throw new IllegalArgumentException("Argument 'factory' cannot be null");

        // validate class
        final boolean isAbstract = Modifier.isAbstract(packetClass.getModifiers());
        if(isAbstract) {
            throw new IllegalArgumentException(
                "Cannot register abstract net-packet class '" + packetClass.getSimpleName() + "'"
            );
        }

        final short packetID = NetPacket.calculatePacketID(packetClass);
        // validate ID
        if(packetFactories.containsKey(packetID)) {
            throw new IllegalStateException(
                "Duplicate net-packet ID: " + packetID + " for '" + packetClass.getSimpleName() + "'"
            );
        }

        this.packetFactories.put(packetID, factory);
        return this;
    }

    public final <H, P extends NetPacket<H>> PacketReader register(Class<P> packetClass) {
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

        final NetPacketFactory<H> factory = (() -> NetPacket.createInstanceReflect(packetClass));
        this.register(packetClass, factory);
        return this;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final PacketReader registerAll(Class<? extends NetPacket<?>>... packetClasses) {
        for(Class<? extends NetPacket<?>> packetClass : packetClasses)
            this.register((Class<NetPacket<Object>>) packetClass);
        return this;
    }

    public final PacketReader registerAllFromPackage(ClasspathResource classpath) {
        final Class<NetPacket<?>>[] packetClasses = classpath.listClasses(NetPacket::isConcretePacketClass);
        return this.registerAll(packetClasses);
    }

    public final PacketReader registerAllFromPackageRecursive(ClasspathResource classpath) {
        final Class<NetPacket<?>>[] packetClasses = classpath.listClassesRecursive(NetPacket::isConcretePacketClass);
        return this.registerAll(packetClasses);
    }


    @SuppressWarnings("unchecked")
    public <H> NetPacketFactory<H> getFactory(short packetID) {
        return (NetPacketFactory<H>) packetFactories.get(packetID);
    }


    public <H> NetPacket<H> read(byte[] data) throws IOException, IllegalStateException {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        // check if data at least contains packetID
        if(data.length < Short.BYTES)
            throw new IllegalArgumentException("Size of 'data' is too small to read net-packet");

        final BinaryInputStream stream = new BinaryInputStream(data);
        try {
            // create packet with read packetID
            final short packetID = stream.readShort();

            final NetPacketFactory<H> factory = this.getFactory(packetID);
            if(factory == null)
                throw new IllegalArgumentException("A net-packet with ID '" + packetID + "' not found");

            final NetPacket<H> packet = factory.create();
            // read data
            packet.read(stream);

            return packet;
        } catch (IOException e) {
            throw new IOException("Failed to read net-packet.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create net-packet with factory.", e);
        }
    }

    public <H> NetPacket<H> readOrNull(byte[] data) {
        try {
            return this.read(data);
        } catch(IOException | IllegalStateException ignored) {
            return null;
        }
    }

    public <H> Optional<NetPacket<H>> tryRead(byte[] data) {
        try {
            final NetPacket<H> packet = this.read(data);
            return Optional.of(packet);
        } catch(IOException | IllegalStateException ignored) {
            return Optional.empty();
        }
    }

}