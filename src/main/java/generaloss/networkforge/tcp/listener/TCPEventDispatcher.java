package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.processor.TCPProcessorPipeline;
import generaloss.resourceflow.ResUtils;
import generaloss.resourceflow.stream.BinaryInputStream;

public class TCPEventDispatcher {

    private TCPConnectable onConnect;
    private TCPCloseable onClose;
    private TCPReceiver onReceive;
    private TCPErrorHandler onError;
    
    private final TCPProcessorPipeline processorPipeline;
    
    public TCPEventDispatcher() {
        this.processorPipeline = new TCPProcessorPipeline(this);
    }
    
    public TCPProcessorPipeline getProcessorPipeline() {
        return processorPipeline;
    }
    

    public void setOnConnect(TCPConnectable onConnect) {
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
        final boolean notCancelled = processorPipeline.processLayerByLayer(
            (processor ->
                processor.onConnect(connection)),
            (throwable ->
                this.invokeOnError(connection, TCPErrorSource.PROCESSOR_CONNECT_CALLBACK, throwable))
        );
        if(notCancelled)
            this.invokeOnConnectDirectly(connection);
    }

    public void invokeOnConnectDirectly(TCPConnection connection) {
        if(onConnect == null)
            return;
        try {
            onConnect.connect(connection);
        } catch (Throwable onConnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.CONNECT_CALLBACK, onConnectThrowable);
        }
    }

    public void invokeOnDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        final boolean notCancelled = processorPipeline.processLayerByLayer(
            (processor ->
                processor.onDisconnect(connection, reason, e)),
            (throwable ->
                this.invokeOnError(connection, TCPErrorSource.PROCESSOR_DISCONNECT_CALLBACK, throwable))
        );
        if(notCancelled)
            this.invokeOnDisconnectDirectly(connection, reason, e);
    }

    public void invokeOnDisconnectDirectly(TCPConnection connection, TCPCloseReason reason, Exception e) {
        if(onClose == null)
            return;
        try {
            onClose.close(connection, reason, e);
        } catch (Throwable onDisconnectThrowable) {
            this.invokeOnError(connection, TCPErrorSource.DISCONNECT_CALLBACK, onDisconnectThrowable);
        }
    }

    public void invokeOnReceive(TCPConnection connection, byte[] byteArray) {
        final boolean notCancelled = processorPipeline.processLayerByLayer(
            (processor ->
                processor.onReceive(connection, byteArray)),
            (throwable ->
                this.invokeOnError(connection, TCPErrorSource.PROCESSOR_RECEIVE_CALLBACK, throwable))
        );
        if(notCancelled)
            this.invokeOnReceiveDirectly(connection, byteArray);
    }

    public void invokeOnReceiveDirectly(TCPConnection connection, byte[] byteArray) {
        if(onReceive == null)
            return;
        try {
            onReceive.receive(connection, byteArray);
        } catch (Throwable onReceiveThrowable) {
            this.invokeOnError(connection, TCPErrorSource.RECEIVE_CALLBACK, onReceiveThrowable);
        }
    }

    public void invokeOnError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        final boolean notCancelled = processorPipeline.processLayerByLayer(
            (processor ->
                processor.onError(connection, source, throwable)),
            (processorOnErrorThrowable ->
                this.printError(connection, TCPErrorSource.PROCESSOR_ERROR_CALLBACK, processorOnErrorThrowable))
        );
        if(notCancelled)
            this.invokeOnErrorDirectly(connection, source, throwable);
    }

    public void invokeOnErrorDirectly(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        if(onError == null)
            return;

        try {
            onError.error(connection, source, throwable);
        } catch (Throwable onErrorThrowable) {
            this.printError(connection, TCPErrorSource.ERROR_CALLBACK, onErrorThrowable);
        }
    }

    private void printError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        TCPErrorHandler.printErrorCatch("Default", connection, source, throwable);
    }

}
