package generaloss.networkforge.packet;

import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.lang.reflect.Constructor;

public abstract class NetPacket<H> {

    private final short ID;

    public NetPacket() {
        this.ID = NetPacket.calculatePacketID(this.getClass());
    }

    public short getPacketID() {
        return ID;
    }


    abstract protected void write(BinaryOutputStream stream) throws IOException;

    abstract protected void read(BinaryInputStream stream) throws IOException;

    abstract public void handle(H handler);


    public BinaryStreamWriter createStreamWriter() {
        return (stream) -> {
            stream.writeShort(ID);
            this.write(stream);
        };
    }

    public byte[] toByteArray() {
        final BinaryStreamWriter streamWriter = this.createStreamWriter();
        return BinaryStreamWriter.writeBytes(streamWriter);
    }


    public static short calculatePacketID(Class<?> packetClass) {
        if(packetClass == null)
            throw new IllegalArgumentException("Argument 'packetClass' cannot be null");

        // check ID annotation
        final PacketID idAnnotation = packetClass.getAnnotation(PacketID.class);
        if(idAnnotation != null)
            return idAnnotation.value();

        // calculate with class name
        final String className = packetClass.getSimpleName();
        final int hash = className.hashCode();
        return (short) ((hash >>> 16) ^ hash);
    }


    @SuppressWarnings("unchecked")
    public static <P extends NetPacket<?>> P createInstanceReflect(Class<P> packetClass) throws IllegalStateException {
        try {
            final Constructor<?> defaultConstructor = NetPacket.getDefaultConstructor(packetClass);
            return (P) defaultConstructor.newInstance();

        }catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate NetPacket: '" + packetClass.getSimpleName() + "'.", e);
        }
    }

    private static Constructor<?> getDefaultConstructor(Class<?> packetClass) throws IllegalStateException {
        try {
            final Constructor<?> constructor = packetClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;

        }catch (NoSuchMethodException e) {
            throw new IllegalStateException("Unable to instantiate NetPacket '" + packetClass.getSimpleName() + "': default (no-args) constructor not found.");
        }
    }

}