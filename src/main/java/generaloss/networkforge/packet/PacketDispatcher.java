package generaloss.networkforge.packet;

import generaloss.resourceflow.stream.BinaryInputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public class PacketDispatcher {

    private final Map<Short, NetPacketFactory<?>> factoryByPacketID;
    private final Queue<PacketHandleTask<?>> toHandleQueue;
    private boolean directHandling;
    private Executor handleExecutor;

    public PacketDispatcher() {
        this.factoryByPacketID = new ConcurrentHashMap<>();
        this.toHandleQueue = new ConcurrentLinkedQueue<>();
        this.directHandling = true;
        this.handleExecutor = Runnable::run;
    }


    public boolean isDirectHandling() {
        return directHandling;
    }

    public PacketDispatcher setDirectHandling(boolean directHandling) {
        this.directHandling = directHandling;
        return this;
    }

    public Executor getHandleExecutor() {
        return handleExecutor;
    }

    public PacketDispatcher setHandleExecutor(Executor handleExecutor) {
        if(handleExecutor == null)
            throw new IllegalArgumentException("Argument 'handleExecutor' cannot be null");

        this.handleExecutor = handleExecutor;
        return this;
    }


    public final <H, P extends NetPacket<H>> PacketDispatcher register(Class<P> packetClass, NetPacketFactory<H> factory) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");
        if(factory == null)
            throw new IllegalArgumentException("Argument 'factory' cannot be null");

        // validate class
        if(Modifier.isAbstract(packetClass.getModifiers()))
            throw new IllegalArgumentException("Cannot register abstract NetPacket class '" + packetClass.getSimpleName() + "'");

        final short packetID = NetPacket.calculatePacketID(packetClass);
        // validate ID
        if(factoryByPacketID.containsKey(packetID))
            throw new IllegalStateException("Duplicate packet ID: " + packetID + " for '" + packetClass.getSimpleName() + "'");

        this.factoryByPacketID.put(packetID, factory);
        return this;
    }

    public final <H, P extends NetPacket<H>> PacketDispatcher register(Class<P> packetClass) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");

        // check default constructor
        try {
            packetClass.getDeclaredConstructor();
        }catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot register NetPacket class '" + packetClass.getSimpleName() + "': default (no-args) constructor not found");
        }

        final NetPacketFactory<H> factory = (() -> NetPacket.createInstanceReflect(packetClass));
        this.register(packetClass, factory);
        return this;
    }

    @SafeVarargs
    public final <H, P extends NetPacket<H>> PacketDispatcher register(Class<P>... packetClasses) {
        for(Class<P> packetClass : packetClasses)
            this.register(packetClass);

        return this;
    }


    @SuppressWarnings("unchecked")
    public <H> NetPacketFactory<H> getPacketFactory(short packetID) {
        final NetPacketFactory<H> packetFactory = (NetPacketFactory<H>) factoryByPacketID.get(packetID);
        if(packetFactory == null)
            throw new IllegalArgumentException("NetPacket with ID '" + packetID + "' not found");

        return packetFactory;
    }

    public <H> void dispatch(byte[] byteArray, H handler) throws IllegalStateException, UncheckedIOException {
        if(byteArray == null)
            throw new IllegalArgumentException("Argument 'byteArray' cannot be null");
        if(handler == null)
            throw new IllegalArgumentException("Argument 'handler' cannot be null");

        // check if data at least contains packetID
        if(byteArray.length < Short.BYTES)
            throw new IllegalArgumentException("The 'byteArray' data is too small for NetPacket to read");

        final BinaryInputStream stream = new BinaryInputStream(byteArray);
        try {
            // create packet with read packetID
            final short packetID = stream.readShort();
            final NetPacketFactory<H> factory = this.getPacketFactory(packetID);
            final NetPacket<H> packet = factory.create();
            // read data
            packet.read(stream);

            // handle / to handle queue
            if(directHandling) {
                handleExecutor.execute(() -> PacketHandleTask.executePacketHandle(packet, handler));
            }else{
                final PacketHandleTask<?> task = new PacketHandleTask<>(packet, handler);
                toHandleQueue.add(task);
            }
        }catch (IOException e) {
            throw new UncheckedIOException("Failed to read NetPacket.", e);
        }catch (Exception e) {
            throw new IllegalStateException("Cannot create NetPacket with factory.", e);
        }
    }

    public synchronized int handlePackets() {
        int handledNum = 0;

        while(!toHandleQueue.isEmpty()){
            final PacketHandleTask<?> task = toHandleQueue.poll();
            handleExecutor.execute(task::executePacketHandle);
            handledNum++;
        }

        return handledNum;
    }

}