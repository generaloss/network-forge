package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;

public abstract class EventHandler {

    public boolean handleConnect(EventInvocationContext context) {
        return true;
    }

    public boolean handleDisconnect(EventInvocationContext context, CloseReason reason, Exception e) {
        return true;
    }

    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        return true;
    }

    public boolean handleError(EventInvocationContext context, ErrorSource source, Throwable throwable) {
        return true;
    }

    public byte[] handleSend(EventInvocationContext context, byte[] data) {
        return data;
    }

}
