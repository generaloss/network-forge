package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.DefaultSendable;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;

public class EventHandleContext implements DefaultSendable {

    private final TCPConnection connection;
    private final EventHandlerPipeline handlerPipeline;

    private int index;

    public EventHandleContext(EventHandlerPipeline handlerPipeline, TCPConnection connection) {
        this.handlerPipeline = handlerPipeline;
        this.connection = connection;
    }

    public TCPConnection getConnection() {
        return connection;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public boolean isClosed() {
        return connection.isClosed();
    }

    @Override
    public boolean send(byte[] byteArray) {
        if(byteArray == null)
            return false;

        final int nextIndex = (index + 1);

        final byte[] processedData = handlerPipeline.fireOnSend(nextIndex, connection, byteArray);
        return connection.sendDirect(processedData);
    }


    public void fireOnConnectNext(TCPConnection connection) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnConnect(nextIndex, connection);
    }

    public void fireOnDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnDisconnect(nextIndex, connection, reason, e);
    }

    public void fireOnReceive(TCPConnection connection, byte[] byteArray) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnReceive(nextIndex, connection, byteArray);
    }

}
