package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.*;

import java.util.LinkedList;
import java.util.List;

public class ListenersHolder {

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


    public boolean handleConnect(TCPConnection connection) {
        for(ConnectListener onConnect : connectListeners)
            onConnect.onConnect(connection);
        return true;
    }

    public void handleDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        for(DisconnectListener onDisconnect : disconnectListener)
            onDisconnect.onDisconnect(connection, reason, e);
    }

    public boolean handleReceive(TCPConnection connection, byte[] data) {
        for(DataListener onReceive : dataListeners)
            onReceive.onReceive(connection, data);
        return true;
    }

    public boolean handleError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        for(ErrorListener onError : errorListeners)
            onError.onError(connection, source, throwable);
        return true;
    }

}
