package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.event.CloseReason;
import generaloss.networkforge.tcp.event.ErrorSource;

public abstract class EventHandlerLayer {

    public boolean handleConnect(PipelineContext context) {
        return true;
    }

    public boolean handleDisconnect(PipelineContext context, CloseReason reason, Exception e) {
        return true;
    }

    public boolean handleReceive(PipelineContext context, byte[] byteArray) {
        return true;
    }

    public byte[] handleSend(PipelineContext context, byte[] byteArray) {
        return byteArray;
    }

    public boolean handleError(PipelineContext context, ErrorSource source, Throwable throwable) {
        return true;
    }

}
