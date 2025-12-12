package generaloss.networkforge.tcp.event;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryInputStream;

public class EventDispatcher {

    private ConnectionListener onConnect;
    private CloseCallback onClose;
    private DataReceiver onReceive;
    private ErrorHandler onError;

    private final ErrorHandler defaultErrorHandler;

    public EventDispatcher(ErrorHandler defaultErrorHandler) {
        if(defaultErrorHandler == null)
            throw new IllegalArgumentException("Argument 'defaultErrorHandler' cannot be null");

        this.onError = defaultErrorHandler;
        this.defaultErrorHandler = defaultErrorHandler;
    }
    

    public void setOnConnect(ConnectionListener onConnect) {
        this.onConnect = onConnect;
    }

    public void setOnDisconnect(CloseCallback onClose) {
        this.onClose = onClose;
    }

    public void setOnReceive(DataReceiver onReceive) {
        this.onReceive = onReceive;
    }

    public void setOnReceiveStream(StreamDataReceiver onReceive) {
        this.onReceive = (sender, byteArray) -> {
            final BinaryInputStream stream = new BinaryInputStream(byteArray);
            onReceive.onReceive(sender, stream);
            ResUtils.close(stream);
        };
    }

    public void setOnError(ErrorHandler onError) {
        this.onError = onError;
    }


    public void invokeOnConnect(TCPConnection connection) {
        if(onConnect == null)
            return;
        try {
            onConnect.onConnect(connection);
        } catch (Throwable onConnectThrowable) {
            this.invokeOnError(connection, ErrorSource.CONNECT_CALLBACK, onConnectThrowable);
        }
    }

    public void invokeOnDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        if(onClose == null)
            return;
        try {
            onClose.onClose(connection, reason, e);
        } catch (Throwable onDisconnectThrowable) {
            this.invokeOnError(connection, ErrorSource.DISCONNECT_CALLBACK, onDisconnectThrowable);
        }
    }

    public void invokeOnReceive(TCPConnection connection, byte[] byteArray) {
        if(onReceive == null)
            return;
        try {
            onReceive.onReceive(connection, byteArray);
        } catch (Throwable onReceiveThrowable) {
            this.invokeOnError(connection, ErrorSource.RECEIVE_CALLBACK, onReceiveThrowable);
        }
    }

    public void invokeOnError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        if(onError == null)
            return;

        try {
            onError.onError(connection, source, throwable);
        } catch (Throwable onErrorThrowable) {
            defaultErrorHandler.onError(connection, ErrorSource.ERROR_CALLBACK, onErrorThrowable);
        }
    }

}
