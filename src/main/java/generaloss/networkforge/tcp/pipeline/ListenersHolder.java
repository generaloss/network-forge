package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.*;

import java.util.LinkedList;
import java.util.List;

public class ListenersHolder {

    private final List<ConnectListener> connectListeners;
    private final List<DisconnectListener> disconnectListener;
    private final List<DataListener> receiveListeners;
    private final List<ErrorListener> errorListeners;
    private final List<DataListener> sendListeners;

    public ListenersHolder() {
        this.connectListeners = new LinkedList<>();
        this.disconnectListener = new LinkedList<>();
        this.receiveListeners = new LinkedList<>();
        this.errorListeners = new LinkedList<>();
        this.sendListeners = new LinkedList<>();
    }

    public void registerOnConnect(ConnectListener onConnect) {
        connectListeners.add(onConnect);
    }

    public void registerOnDisconnect(DisconnectListener onClose) {
        disconnectListener.add(onClose);
    }
    
    public void registerOnReceive(DataListener onReceive) {
        receiveListeners.add(onReceive);
    }
    
    public void registerOnError(ErrorListener onError) {
        errorListeners.add(onError);
    }

    public void registerOnSend(DataListener onSend) {
        sendListeners.add(onSend);
    }


    public boolean unregisterOnConnect(ConnectListener onConnect) {
        return connectListeners.remove(onConnect);
    }

    public boolean unregisterOnDisconnect(DisconnectListener onClose) {
        return disconnectListener.remove(onClose);
    }

    public boolean unregisterOnReceive(DataListener onReceive) {
        return receiveListeners.remove(onReceive);
    }

    public boolean unregisterOnError(ErrorListener onError) {
        return errorListeners.remove(onError);
    }

    public boolean unregisterOnSend(DataListener onSend) {
        return sendListeners.remove(onSend);
    }


    public void invokeConnect(TCPConnection connection) {
        for(ConnectListener onConnect : connectListeners)
            onConnect.onConnect(connection);
    }

    public void invokeDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        for(DisconnectListener onDisconnect : disconnectListener)
            onDisconnect.onDisconnect(connection, reason, e);
    }

    public void invokeReceive(TCPConnection connection, byte[] data) {
        for(DataListener onReceive : receiveListeners)
            onReceive.onData(connection, data);
    }

    public void invokeError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        for(ErrorListener onError : errorListeners)
            onError.onError(connection, source, throwable);
    }

    public void invokeSend(TCPConnection connection, byte[] data) {
        for(DataListener onSend : sendListeners)
            onSend.onData(connection, data);
    }

}
