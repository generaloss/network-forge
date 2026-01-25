package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.*;

import java.util.LinkedList;
import java.util.List;

public class ListenersHolder extends EventHandlerLayer {

    private final List<ConnectListener> connectListeners;
    private final List<DisconnectListener> disconnectListener;
    private final List<DataListener> dataListeners;
    private final List<ErrorListener> errorListeners;

    public ListenersHolder() {
        this.connectListeners = new LinkedList<>();
        this.disconnectListener = new LinkedList<>();
        this.dataListeners = new LinkedList<>();
        this.errorListeners = new LinkedList<>();
    }

    public void registerOnConnect(ConnectListener onConnect) {
        connectListeners.add(onConnect);
    }

    public void registerOnDisconnect(DisconnectListener onClose) {
        disconnectListener.add(onClose);
    }

    public void registerOnReceive(DataListener onReceive) {
        dataListeners.add(onReceive);
    }

    public void registerOnError(ErrorListener onError) {
        errorListeners.add(onError);
    }


    public boolean unregisterOnConnect(ConnectListener onConnect) {
        return connectListeners.remove(onConnect);
    }

    public boolean unregisterOnDisconnect(DisconnectListener onClose) {
        return disconnectListener.remove(onClose);
    }

    public boolean unregisterOnReceive(DataListener onReceive) {
        return dataListeners.remove(onReceive);
    }

    public boolean unregisterOnError(ErrorListener onError) {
        return errorListeners.remove(onError);
    }


    @Override
    public boolean handleConnect(EventPipelineContext context) {
        final TCPConnection connection = context.getConnection();
        for(ConnectListener onConnect : connectListeners)
            onConnect.onConnect(connection);
        return true;
    }

    @Override
    public void handleDisconnect(EventPipelineContext context, CloseReason reason, Exception e) {
        final TCPConnection connection = context.getConnection();
        for(DisconnectListener onDisconnect : disconnectListener)
            onDisconnect.onDisconnect(connection, reason, e);
    }

    @Override
    public boolean handleReceive(EventPipelineContext context, byte[] data) {
        final TCPConnection connection = context.getConnection();
        for(DataListener onReceive : dataListeners)
            onReceive.onReceive(connection, data);
        return true;
    }

    @Override
    public boolean handleError(EventPipelineContext context, ErrorSource source, Throwable throwable) {
        final TCPConnection connection = context.getConnection();
        for(ErrorListener onError : errorListeners)
            onError.onError(connection, source, throwable);
        return true;
    }

}
