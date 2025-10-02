package generaloss.networkforge.packet;

import generaloss.resourceflow.stream.BinaryInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetPacketDispatcher {

    private final Map<Short, NetPacketFactory<?>> factoryByPacketID;
    private final Queue<Runnable> toHandleQueue;

    public NetPacketDispatcher() {
        this.factoryByPacketID = new ConcurrentHashMap<>();
        this.toHandleQueue = new ConcurrentLinkedQueue<>();
    }

    public final <H, P extends NetPacket<H>> NetPacketDispatcher register(Class<P> packetClass, NetPacketFactory<H> factory) {
        final short packetID = NetPacket.calculatePacketID(packetClass);
        this.factoryByPacketID.put(packetID, factory);
        return this;
    }

    public final <H, P extends NetPacket<H>> NetPacketDispatcher register(Class<P> packetClass) {
        final NetPacketFactory<H> factory = () -> createPacketInstance(packetClass);
        this.register(packetClass, factory);
        return this;
    }

    @SafeVarargs
    public final <H, P extends NetPacket<H>> NetPacketDispatcher register(Class<P>... packetClasses) {
        for(Class<P> packetClass : packetClasses)
            this.register(packetClass);
        return this;
    }

    @SuppressWarnings("unchecked")
    private static <P extends NetPacket<?>> P createPacketInstance(Class<P> packetClass) {
        try{
            final Constructor<?> constructor = packetClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (P) constructor.newInstance();

        }catch(Exception e){
            throw new IllegalStateException("Unable to instance packet: " + packetClass.getName(), e);
        }
    }


    @SuppressWarnings("unchecked")
    public <H> boolean readPacket(byte[] byteArray, H handler) {
        try{
            if(byteArray.length < 2) // 'short' (ID) size = 2
                return false;

            // create stream from byteArray
            final BinaryInputStream dataStream = new BinaryInputStream(new ByteArrayInputStream(byteArray));

            // read ID and get packet factory
            final short ID = dataStream.readShort();

            final NetPacketFactory<H> packetFactory = (NetPacketFactory<H>) factoryByPacketID.get(ID);
            if(packetFactory == null)
                return false;

            // create packet class instance and read remaining data
            final NetPacket<H> packetInstance = packetFactory.create();
            packetInstance.read(dataStream);

            // handle and return
            toHandleQueue.add(() -> packetInstance.handle(handler));
            return true;

        }catch(IOException e) {
            throw new UncheckedIOException("Unable to read packet", e);
        }
    }

    public int handlePackets() {
        int count = 0;
        while(!toHandleQueue.isEmpty()){
            final Runnable handleRunnable = toHandleQueue.poll();
            count++;
            handleRunnable.run();
        }
        return count;
    }

}