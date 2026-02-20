package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorListener;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class EventPipelineContext {

    private final EventPipeline pipeline;
    private final TCPConnection connection;
    private int handlerIndex;

    public EventPipelineContext(EventPipeline pipeline, TCPConnection connection) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");

        this.pipeline = pipeline;
        this.connection = connection;
    }

    public EventPipeline getEventPipeline() {
        return pipeline;
    }

    public TCPConnection getConnection() {
        return connection;
    }


    public void setHandlerIndex(int handlerIndex) {
        this.handlerIndex = handlerIndex;
    }

    public int getHandlerIndex() {
        return handlerIndex;
    }


    protected boolean invokeConnect() {
        if(handlerIndex == pipeline.getHandlersCount()) {
            pipeline.getTarget().handleConnect(connection);
            return false; // break
        }
        
        try {
            final EventHandlerLayer handler = pipeline.getHandler(handlerIndex);
            return handler.handleConnect(this);

        } catch (Throwable t) {
            this.fireError(ErrorSource.CONNECT_HANDLER, t);
            return true; // skip
        }
    }

    protected boolean invokeDisconnect(CloseReason reason, Exception e) {
        if(handlerIndex == pipeline.getHandlersCount()) {
            pipeline.getTarget().handleDisconnect(connection, reason, e);
            return false; // break
        }
        
        try {
            final EventHandlerLayer handler = pipeline.getHandler(handlerIndex);
            handler.handleDisconnect(this, reason, e);
            return true;

        } catch (Throwable t) {
            this.fireError(ErrorSource.DISCONNECT_HANDLER, t);
            return true; // skip
        }
    }

    protected boolean invokeReceive(byte[] data) {
        if(handlerIndex == pipeline.getHandlersCount()) {
            pipeline.getTarget().handleReceive(connection, data);
            return false; // break
        }
        
        try {
            final EventHandlerLayer handler = pipeline.getHandler(handlerIndex);
            return handler.handleReceive(this, data);

        } catch (Throwable t) {
            this.fireError(ErrorSource.RECEIVE_HANDLER, t);
            return true; // skip
        }
    }

    private byte[] sendProcessedData;

    protected boolean invokeSend(byte[] data) {
        try {
            final EventHandlerLayer handler = pipeline.getHandler(handlerIndex);
            sendProcessedData = handler.handleSend(this, data);
            return (sendProcessedData != null);

        } catch (Throwable t) {
            this.fireError(ErrorSource.SEND_HANDLER, t);
            return true; // skip
        }
    }

    protected byte[] getSendProcessedData() {
        return sendProcessedData;
    }

    protected boolean invokeError(ErrorSource source, Throwable throwable) {
        if(handlerIndex == pipeline.getHandlersCount()) {
            pipeline.getTarget().handleError(connection, source, throwable);
            return false; // break
        }
        
        try {
            final EventHandlerLayer handler = pipeline.getHandler(handlerIndex);
            return handler.handleError(this, source, throwable);

        } catch (Throwable t) {
            ErrorListener.printErrorCatch(connection, ErrorSource.ERROR_HANDLER, t);
            return true; // skip
        }
    }


    public boolean fireSend(TCPConnection connection, byte[] data) {
        if(connection.isClosed())
            return false;

        return pipeline.fireSend(handlerIndex - 1, connection, data);
    }

    public boolean fireSend(byte[] data) {
        return this.fireSend(connection, data);
    }

    public boolean fireSend(TCPConnection connection, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);
        return this.fireSend(connection, byteArray);
    }

    public boolean fireSend(ByteBuffer buffer) {
        return this.fireSend(connection, buffer);
    }

    public boolean fireSend(TCPConnection connection, String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.fireSend(connection, string.getBytes());
    }

    public boolean fireSend(String string) {
        return this.fireSend(connection, string);
    }

    public boolean fireSend(TCPConnection connection, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.fireSend(connection, byteArray);

        } catch (IOException e) {
            final int nextIndex = (handlerIndex + 1);
            pipeline.fireError(nextIndex, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(BinaryStreamWriter streamWriter) {
        return this.fireSend(connection, streamWriter);
    }

    public boolean fireSend(TCPConnection connection, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.fireSend(connection, byteArray);

        } catch (IOException e) {
            final int nextIndex = (handlerIndex + 1);
            pipeline.fireError(nextIndex, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(NetPacket<?> packet) {
        return this.fireSend(connection, packet);
    }


    public void fireConnect(TCPConnection connection) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireConnect(nextIndex, connection);
    }

    public void fireConnect() {
        this.fireConnect(connection);
    }


    public void fireDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireDisconnect(nextIndex, connection, reason, e);
    }

    public void fireDisconnect(CloseReason reason, Exception e) {
        this.fireDisconnect(connection, reason, e);
    }


    public void fireReceive(TCPConnection connection, byte[] data) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireReceive(nextIndex, connection, data);
    }

    public void fireReceive(byte[] data) {
        this.fireReceive(connection, data);
    }


    public void fireError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireError(nextIndex, connection, source, throwable);
    }

    public void fireError(ErrorSource source, Throwable throwable) {
        this.fireError(connection, source, throwable);
    }

}
