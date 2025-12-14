package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.*;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryInputStream;

import java.util.LinkedList;
import java.util.List;

public class EventListenerHolder extends EventHandlerLayer {

    private final List<ConnectionListener> connectListeners;
    private final List<CloseCallback> disconnectCallbacks;
    private final List<DataReceiver> dataReceivers;
    private final List<ErrorHandler> errorHandlers;

    public EventListenerHolder() {
        this.connectListeners = new LinkedList<>();
        this.disconnectCallbacks = new LinkedList<>();
        this.dataReceivers = new LinkedList<>();
        this.errorHandlers = new LinkedList<>();
    }

    public void registerOnConnect(ConnectionListener onConnect) {
        connectListeners.add(onConnect);
    }

    public void registerOnDisconnect(CloseCallback onClose) {
        disconnectCallbacks.add(onClose);
    }

    public void registerOnReceive(DataReceiver onReceive) {
        dataReceivers.add(onReceive);
    }

    public void registerOnReceiveStream(StreamDataReceiver onReceive) {
        this.registerOnReceive((sender, byteArray) -> {
            final BinaryInputStream stream = new BinaryInputStream(byteArray);
            onReceive.onReceive(sender, stream);
            ResUtils.close(stream);
        });
    }

    public void registerOnError(ErrorHandler onError) {
        errorHandlers.add(onError);
    }


    public boolean unregisterOnConnect(ConnectionListener onConnect) {
        return connectListeners.remove(onConnect);
    }

    public boolean unregisterOnDisconnect(CloseCallback onClose) {
        return disconnectCallbacks.remove(onClose);
    }

    public boolean unregisterOnReceive(DataReceiver onReceive) {
        return dataReceivers.remove(onReceive);
    }

    public boolean unregisterOnError(ErrorHandler onError) {
        return errorHandlers.remove(onError);
    }


    @Override
    public boolean handleConnect(TCPConnection connection) {
        for(ConnectionListener onConnect : connectListeners)
            onConnect.onConnect(connection);
        return true;
    }

    @Override
    public boolean handleDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        for(CloseCallback onDisconnect : disconnectCallbacks)
            onDisconnect.onClose(connection, reason, e);
        return true;
    }

    @Override
    public boolean handleReceive(TCPConnection connection, byte[] byteArray) {
        for(DataReceiver onReceive : dataReceivers)
            onReceive.onReceive(connection, byteArray);
        return true;
    }

    @Override
    public boolean handleError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        for(ErrorHandler onError : errorHandlers)
            onError.onError(connection, source, throwable);
        return true;
    }

}
