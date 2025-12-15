package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.event.CloseReason;
import generaloss.networkforge.tcp.event.ErrorSource;

public abstract class EventHandlerLayer {

    public boolean handleConnect(EventHandleContext context) {
        return true;
    }

    public boolean handleDisconnect(EventHandleContext context, CloseReason reason, Exception e) {
        return true;
    }

    public boolean handleReceive(EventHandleContext context, byte[] byteArray) {
        return true;
    }

    public byte[] handleSend(EventHandleContext context, byte[] byteArray) {
        return byteArray;
    }

    public boolean handleError(EventHandleContext context, ErrorSource source, Throwable throwable) {
        return true;
    }

}
