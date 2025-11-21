package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryInputStream;

import java.util.function.Consumer;

public class TCPListenerHolder {

    private Consumer<TCPConnection> onConnect;
    private TCPCloseable onClose;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;

    public void setOnConnect(Consumer<TCPConnection> onConnect) {
        this.onConnect = onConnect;
    }

    public void setOnDisconnect(TCPCloseable onClose) {
        this.onClose = onClose;
    }

    public void setOnReceive(TCPReceiver onReceive) {
        this.onReceive = onReceive;
    }

    public void setOnReceiveStream(TCPReceiverStream onReceive) {
        this.onReceive = (sender, byteArray) -> {
            final BinaryInputStream stream = new BinaryInputStream(byteArray);
            onReceive.receive(sender, stream);
            ResUtils.close(stream);
        };
    }

    public void setOnError(TCPErrorHandler onError) {
        this.onError = onError;
    }


    public void invokeOnConnect(TCPConnection connection) {
        if(onConnect == null)
            return;

        try {
            onConnect.accept(connection);
        } catch (Throwable onConnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.CONNECT_CALLBACK, onConnectThrowable);
        }
    }

    public void invokeOnDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        if(onClose == null)
            return;

        try {
            onClose.close(connection, reason, e);
        } catch (Throwable onCloseThrowable) {
            this.invokeOnError(connection, TCPErrorSource.DISCONNECT_CALLBACK, onCloseThrowable);
        }
    }

    public void invokeOnReceive(TCPConnection connection, byte[] byteArray) {
        if(onReceive == null || byteArray == null)
            return;

        try {
            onReceive.receive(connection, byteArray);
        } catch (Throwable onReceiveThrowable) {
            this.invokeOnError(connection, TCPErrorSource.RECEIVE_CALLBACK, onReceiveThrowable);
        }
    }

    public void invokeOnError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        try {
            onError.error(connection, source, throwable);
        } catch (Throwable onErrorThrowable) {
            final Class<?> clazz = this.getClass();
            System.out.println(clazz);
            TCPErrorHandler.printErrorCatch(clazz, connection, TCPErrorSource.ERROR_CALLBACK, onErrorThrowable);
        }
    }

}
