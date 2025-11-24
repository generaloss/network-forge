package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.processor.TCPProcessorPipeline;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryInputStream;

import java.util.function.Consumer;

public class TCPEventDispatcher {

    private Consumer<TCPConnection> onConnect;
    private TCPCloseable onClose;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;
    
    private final TCPProcessorPipeline processorPipeline;
    
    public TCPEventDispatcher() {
        this.processorPipeline = new TCPProcessorPipeline();
    }
    
    public TCPProcessorPipeline getProcessorPipeline() {
        return processorPipeline;
    }
    

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
        final boolean cancelled = !processorPipeline.processLayerByLayer(
            (processor ->
                processor.onConnect(connection)),
            (throwable ->
                this.invokeOnError(connection, TCPErrorSource.PROCESSOR_CONNECT_CALLBACK, throwable))
        );

        if(cancelled || onConnect == null)
            return;

        try {
            onConnect.accept(connection);
        } catch (Throwable onConnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.CONNECT_CALLBACK, onConnectThrowable);
        }
    }

    public void invokeOnDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        final boolean cancelled = !processorPipeline.processLayerByLayer(
            (processor ->
                processor.onDisconnect(connection, reason, e)),
            (throwable ->
                this.invokeOnError(connection, TCPErrorSource.PROCESSOR_DISCONNECT_CALLBACK, throwable))
        );

        if(cancelled || onClose == null)
            return;

        try {
            onClose.close(connection, reason, e);
        } catch (Throwable onDisconnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.DISCONNECT_CALLBACK, onDisconnectThrowable);
        }
    }

    public void invokeOnReceive(TCPConnection connection, byte[] byteArray) {
        if(byteArray == null)
            return;

        final boolean cancelled = !processorPipeline.processLayerByLayer(
            (processor ->
                processor.onReceive(connection, byteArray)),
            (throwable ->
                this.invokeOnError(connection, TCPErrorSource.PROCESSOR_RECEIVE_CALLBACK, throwable))
        );

        if(cancelled || onReceive == null)
            return;

        try {
            onReceive.receive(connection, byteArray);
        } catch (Throwable onReceiveThrowable) {
            this.invokeOnError(connection, TCPErrorSource.RECEIVE_CALLBACK, onReceiveThrowable);
        }
    }

    public void invokeOnError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        final boolean cancelled = !processorPipeline.processLayerByLayer(
            (processor ->
                processor.onError(connection, source, throwable)),
            (processorOnErrorThrowable ->
                this.printError(connection, TCPErrorSource.PROCESSOR_ERROR_CALLBACK, processorOnErrorThrowable))
        );

        if(cancelled)
            return;

        try {
            onError.error(connection, source, throwable);
        } catch (Throwable onErrorThrowable) {
            this.printError(connection, TCPErrorSource.ERROR_CALLBACK, onErrorThrowable);
        }
    }

    private void printError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        final Class<?> clazz = this.getClass(); // TODO
        System.out.println(clazz); // TODO: remove
        TCPErrorHandler.printErrorCatch(clazz, connection, source, throwable);
    }

}
