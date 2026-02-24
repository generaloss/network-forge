package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorListener;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.nio.ByteBuffer;

public class EventInvocationContext {

    private final EventPipeline pipeline;
    private final TCPConnection connection; // can be null
    private final EventHandler[] handlersShapshot;
    private int handlerIndex;

    public EventInvocationContext(EventPipeline pipeline, TCPConnection connection, EventHandler[] handlersShapshot) {
        this.pipeline = pipeline;
        this.connection = connection;
        this.handlersShapshot = handlersShapshot; // preserve handlers state within a single event
    }

    public EventPipeline getEventPipeline() {
        return pipeline;
    }

    public TCPConnection getConnection() {
        return connection;
    }

    public EventHandler[] getHandlersShapshot() {
        return handlersShapshot;
    }


    protected void setHandlerIndex(int handlerIndex) {
        this.handlerIndex = handlerIndex;
    }

    public int getHandlerIndex() {
        return handlerIndex;
    }


    protected boolean invokeConnect() {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeConnect(connection);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleConnect(this);

        } catch (Throwable t) {
            this.fireError(ErrorSource.CONNECT_HANDLER, t);
            return true; // skip
        }
    }

    protected boolean invokeDisconnect(CloseReason reason, Exception e) {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeDisconnect(connection, reason, e);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleDisconnect(this, reason, e);

        } catch (Throwable t) {
            this.fireError(ErrorSource.DISCONNECT_HANDLER, t);
            return true; // skip
        }
    }

    protected boolean invokeReceive(byte[] data) {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeReceive(connection, data);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleReceive(this, data);

        } catch (Throwable t) {
            this.fireError(ErrorSource.RECEIVE_HANDLER, t);
            return true; // skip
        }
    }


    protected boolean invokeError(ErrorSource source, Throwable throwable) {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeError(connection, source, throwable);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleError(this, source, throwable);

        } catch (Throwable t) {
            ErrorListener.printErrorCatch(connection, ErrorSource.ERROR_HANDLER, t);
            return true; // skip
        }
    }


    public boolean fireSend(TCPConnection connection, byte[] data) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, data);
    }

    public boolean fireSend(byte[] data) {
        return this.fireSend(connection, data);
    }

    public boolean fireSend(TCPConnection connection, ByteBuffer buffer) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, buffer);
    }

    public boolean fireSend(ByteBuffer buffer) {
        return this.fireSend(connection, buffer);
    }

    public boolean fireSend(TCPConnection connection, String string) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, string);
    }

    public boolean fireSend(String string) {
        return this.fireSend(connection, string);
    }

    public boolean fireSend(TCPConnection connection, BinaryStreamWriter streamWriter) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, streamWriter);
    }

    public boolean fireSend(BinaryStreamWriter streamWriter) {
        return this.fireSend(connection, streamWriter);
    }

    public boolean fireSend(TCPConnection connection, NetPacket<?> packet) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, packet);
    }

    public boolean fireSend(NetPacket<?> packet) {
        return this.fireSend(connection, packet);
    }


    public void fireConnect(TCPConnection connection) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireConnect(handlersShapshot, nextIndex, connection);
    }

    public void fireConnect() {
        this.fireConnect(connection);
    }


    public void fireDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireDisconnect(handlersShapshot, nextIndex, connection, reason, e);
    }

    public void fireDisconnect(CloseReason reason, Exception e) {
        this.fireDisconnect(connection, reason, e);
    }


    public void fireReceive(TCPConnection connection, byte[] data) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireReceive(handlersShapshot, nextIndex, connection, data);
    }

    public void fireReceive(byte[] data) {
        this.fireReceive(connection, data);
    }


    public void fireError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireError(handlersShapshot, nextIndex, connection, source, throwable);
    }

    public void fireError(ErrorSource source, Throwable throwable) {
        this.fireError(connection, source, throwable);
    }

}
