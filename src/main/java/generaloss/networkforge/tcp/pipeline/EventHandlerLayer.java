package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;

public abstract class EventHandlerLayer {

    public boolean handleConnect(EventPipelineContext context) {
        return true;
    }

    public boolean handleDisconnect(EventPipelineContext context, CloseReason reason, Exception e) {
        return true;
    }

    public boolean handleReceive(EventPipelineContext context, byte[] data) {
        return true;
    }

    public boolean handleError(EventPipelineContext context, ErrorSource source, Throwable throwable) {
        return true;
    }

    public byte[] handleSend(EventPipelineContext context, byte[] data) {
        return data;
    }

}
