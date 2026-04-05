package generaloss.networkforge.packet;

public abstract class VisitorNetPacket<H> extends NetPacket {

    public abstract void handle(H handler);

    public Runnable createHandleTask(H handler) {
        return () -> this.handle(handler);
    }

}