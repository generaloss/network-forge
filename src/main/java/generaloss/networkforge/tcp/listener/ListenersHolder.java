package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

import java.util.LinkedList;
import java.util.List;

public class ListenersHolder {

    private final List<TCPConnectionConsumer> connectListeners;
    private final List<DisconnectListener> disconnectListener;
    private final List<DataListener> receiveListeners;
    private final List<TCPConnectionConsumer> readCompleteListeners;
    private final List<ErrorListener> errorListeners;
    private final List<DataListener> sendListeners;

    public ListenersHolder() {
        this.connectListeners = new LinkedList<>();
        this.disconnectListener = new LinkedList<>();
        this.receiveListeners = new LinkedList<>();
        this.readCompleteListeners = new LinkedList<>();
        this.errorListeners = new LinkedList<>();
        this.sendListeners = new LinkedList<>();
    }

    public void registerOnConnect(TCPConnectionConsumer onConnect) {
        connectListeners.add(onConnect);
    }

    public void registerOnDisconnect(DisconnectListener onClose) {
        disconnectListener.add(onClose);
    }

    public void registerOnReceive(DataListener onReceive) {
        receiveListeners.add(onReceive);
    }

    public void registerOnReadComplete(TCPConnectionConsumer onReadComplete) {
        readCompleteListeners.add(onReadComplete);
    }

    public void registerOnError(ErrorListener onError) {
        errorListeners.add(onError);
    }

    public void registerOnSend(DataListener onSend) {
        sendListeners.add(onSend);
    }


    public boolean unregisterOnConnect(TCPConnectionConsumer onConnect) {
        return connectListeners.remove(onConnect);
    }

    public boolean unregisterOnDisconnect(DisconnectListener onClose) {
        return disconnectListener.remove(onClose);
    }

    public boolean unregisterOnReceive(DataListener onReceive) {
        return receiveListeners.remove(onReceive);
    }

    public boolean unregisterOnReadComplete(TCPConnectionConsumer onReadComplete) {
        return readCompleteListeners.remove(onReadComplete);
    }

    public boolean unregisterOnError(ErrorListener onError) {
        return errorListeners.remove(onError);
    }

    public boolean unregisterOnSend(DataListener onSend) {
        return sendListeners.remove(onSend);
    }


    public void invokeConnect(TCPConnection connection) {
        for(TCPConnectionConsumer onConnect : connectListeners)
            onConnect.accept(connection);
    }

    public void invokeDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        for(DisconnectListener onDisconnect : disconnectListener)
            onDisconnect.onDisconnect(connection, reason, e);
    }

    public void invokeReceive(TCPConnection connection, byte[] data) {
        for(DataListener onReceive : receiveListeners)
            onReceive.onData(connection, data);
    }

    public void invokeReadComplete(TCPConnection connection) {
        for(TCPConnectionConsumer onReadComplete : readCompleteListeners)
            onReadComplete.accept(connection);
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
