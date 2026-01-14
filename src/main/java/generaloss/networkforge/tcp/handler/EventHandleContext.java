package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.DefaultSendable;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;

public class EventHandleContext implements DefaultSendable {

    private final TCPConnection connection;
    private final EventPipeline handlerPipeline;

    private int index;

    public EventHandleContext(EventPipeline handlerPipeline, TCPConnection connection) {
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
    public boolean send(byte[] data) {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        final int nextIndex = (index + 1);

        final byte[] processedData = handlerPipeline.fireOnSend(nextIndex, connection, data);
        return connection.sendDirect(processedData);
    }


    public void fireOnConnectNext(TCPConnection connection) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnConnect(nextIndex, connection);
    }

    public void fireOnConnectNext() {
        this.fireOnConnectNext(connection);
    }


    public void fireOnDisconnectNext(TCPConnection connection, CloseReason reason, Exception e) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnDisconnect(nextIndex, connection, reason, e);
    }

    public void fireOnDisconnectNext(CloseReason reason, Exception e) {
        this.fireOnDisconnectNext(connection, reason, e);
    }


    public void fireOnReceiveNext(TCPConnection connection, byte[] data) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnReceive(nextIndex, connection, data);
    }

    public void fireOnReceiveNext(byte[] data) {
        this.fireOnReceiveNext(connection, data);
    }


    public void fireOnErrorNext(TCPConnection connection, ErrorSource source, Throwable throwable) {
        final int nextIndex = (index + 1);
        handlerPipeline.fireOnError(nextIndex, connection, source, throwable);
    }

    public void fireOnErrorNext(ErrorSource source, Throwable throwable) {
        this.fireOnErrorNext(connection, source, throwable);
    }

}
