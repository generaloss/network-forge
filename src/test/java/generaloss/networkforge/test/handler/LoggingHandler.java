package generaloss.networkforge.test.handler;

import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.networkforge.tcp.pipeline.EventHandler;
import generaloss.networkforge.tcp.pipeline.EventInvocationContext;

public class LoggingHandler extends EventHandler {
    @Override
    public boolean handleConnect(EventInvocationContext context) {
        System.out.println("Connected: " + context.getConnection());
        return true;
    }

    @Override
    public boolean handleDisconnect(EventInvocationContext context, CloseReason reason, Exception e) {
        System.out.println("Disconnected: " + reason);
        return true;
    }

    @Override
    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        System.out.println("Received " + data.length + " bytes");
        return true;
    }

    @Override
    public boolean handleError(EventInvocationContext context, ErrorSource source, Throwable throwable) {
        System.out.println("Error (" + source + "): " + throwable.getMessage());
        return true;
    }

    @Override
    public boolean handleSend(EventInvocationContext context, byte[] data) {
        System.out.println("Sending " + data.length + " bytes");
        return true;
    }
}