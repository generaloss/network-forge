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
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot register NetPacket class '" + packetClass.getSimpleName() + "': default (no-args) constructor not found");
        }

        final NetPacketFactory<H> factory = (() -> NetPacket.createInstanceReflect(packetClass));
        this.register(packetClass, factory);
        return this;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final PacketDispatcher registerAll(Class<? extends NetPacket<?>>... packetClasses) {
        for(Class<? extends NetPacket<?>> packetClass : packetClasses)
            this.register((Class<NetPacket<Object>>) packetClass);

        return this;
    }


    @SuppressWarnings("unchecked")
    public <H> NetPacketFactory<H> getPacketFactory(short packetID) {
        final NetPacketFactory<H> packetFactory = (NetPacketFactory<H>) factoryByPacketID.get(packetID);
        if(packetFactory == null)
            throw new IllegalArgumentException("NetPacket with ID '" + packetID + "' not found");

        return packetFactory;
    }

    public <H> void dispatch(byte[] data, NetPacketFunction<H> handlerFunction) throws IllegalStateException, UncheckedIOException {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");
        if(handlerFunction == null)
            throw new IllegalArgumentException("Argument 'handlerFunction' cannot be null");

        // check if data at least contains packetID
        if(data.length < Short.BYTES)
            throw new IllegalArgumentException("Size of 'data' is too small for NetPacket to read");

        final BinaryInputStream stream = new BinaryInputStream(data);
        try {
            // create packet with read packetID
            final short packetID = stream.readShort();
            final NetPacketFactory<H> factory = this.getPacketFactory(packetID);
            final NetPacket<H> packet = factory.create();
            // read data
            packet.read(stream);

            // get handler
            final H handler = handlerFunction.apply(packet);
            if(handler == null)
                return; // do not handle packet

            // handle / to handle queue
            if(directHandling) {
                handleExecutor.execute(() -> PacketHandleTask.executePacketHandle(packet, handler));
            }else{
                final PacketHandleTask<?> task = new PacketHandleTask<>(packet, handler);
                toHandleQueue.add(task);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read NetPacket.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create NetPacket with factory.", e);
        }
    }

    public <H> void dispatch(byte[] data, H handler) throws IllegalStateException, UncheckedIOException {
        final NetPacketFunction<H> handlerFunction = (packet -> handler);
        this.dispatch(data, handlerFunction);
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