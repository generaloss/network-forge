package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorListener;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
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
            pipeline.getTarget().invokeOnConnect(connection);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleConnect(this);

        } catch (Throwable t) {
            this.error(ErrorSource.CONNECT_HANDLER, t);
            return false; // break
        }
    }

    protected boolean invokeDisconnect(CloseReason reason, Exception e) {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeOnDisconnect(connection, reason, e);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleDisconnect(this, reason, e);

        } catch (Throwable t) {
            this.error(ErrorSource.DISCONNECT_HANDLER, t);
            return false; // break
        }
    }

    protected boolean invokeReceive(byte[] data) {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeOnReceive(connection, data);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleReceive(this, data);

        } catch (Throwable t) {
            this.error(ErrorSource.RECEIVE_HANDLER, t);
            return false; // break
        }
    }

    protected boolean invokeReadComplete() {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeOnReadComplete(connection);
            return false; // break
        }

        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleReadComplete(this);

        } catch (Throwable t) {
            this.error(ErrorSource.READ_COMPLETE_HANDLER, t);
            return false; // break
        }
    }

    protected boolean invokeError(ErrorSource source, Throwable throwable) {
        if(handlerIndex == handlersShapshot.length) {
            pipeline.getTarget().invokeOnError(connection, source, throwable);
            return false; // break
        }
        
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleError(this, source, throwable);

        } catch (Throwable t) {
            ErrorListener.printError(connection, ErrorSource.ERROR_HANDLER, t);
            return false; // break
        }
    }

    protected boolean invokeSend(byte[] data) {
        try {
            final EventHandler handler = handlersShapshot[handlerIndex];
            return handler.handleSend(this, data);

        } catch (Throwable t) {
            this.error(ErrorSource.SEND_HANDLER, t);
            return false; // break
        }
    }


    public void connect(TCPConnection connection) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireConnect(handlersShapshot, nextIndex, connection);
    }

    public void connect() {
        this.connect(connection);
    }


    public void disconnect(TCPConnection connection, CloseReason reason, Exception e) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireDisconnect(handlersShapshot, nextIndex, connection, reason, e);
    }

    public void disconnect(CloseReason reason, Exception e) {
        this.disconnect(connection, reason, e);
    }


    public void receive(TCPConnection connection, byte[] data) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireReceive(handlersShapshot, nextIndex, connection, data);
    }

    public void receive(byte[] data) {
        this.receive(connection, data);
    }

    public void receive(TCPConnection connection, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);
        this.receive(connection, byteArray);
    }

    public void receive(ByteBuffer buffer) {
        this.receive(connection, buffer);
    }

    public void receive(TCPConnection connection, String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");
        this.receive(connection, string.getBytes());
    }

    public void receive(String string) {
        this.receive(connection, string);
    }

    public boolean receive(TCPConnection connection, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            this.receive(connection, byteArray);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean receive(BinaryStreamWriter streamWriter) {
        return this.receive(connection, streamWriter);
    }

    public boolean receive(TCPConnection connection, NetPacket packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            this.receive(connection, byteArray);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean receive(NetPacket packet) {
        return this.receive(connection, packet);
    }


    public void readComplete(TCPConnection connection) {
        final int nextIndex = (handlerIndex - 1);
        pipeline.fireReadComplete(handlersShapshot, nextIndex, connection);
    }

    public void readComplete() {
        this.readComplete(connection);
    }


    public boolean send(TCPConnection connection, byte[] data) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, data);
    }

    public boolean send(byte[] data) {
        return this.send(connection, data);
    }

    public boolean send(TCPConnection connection, ByteBuffer buffer) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, buffer);
    }

    public boolean send(ByteBuffer buffer) {
        return this.send(connection, buffer);
    }

    public boolean send(TCPConnection connection, String string) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, string);
    }

    public boolean send(String string) {
        return this.send(connection, string);
    }

    public boolean send(TCPConnection connection, BinaryStreamWriter streamWriter) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, streamWriter);
    }

    public boolean send(BinaryStreamWriter streamWriter) {
        return this.send(connection, streamWriter);
    }

    public boolean send(TCPConnection connection, NetPacket packet) {
        final int nextIndex = (handlerIndex - 1);
        return pipeline.fireSend(handlersShapshot, nextIndex, connection, packet);
    }

    public boolean send(NetPacket packet) {
        return this.send(connection, packet);
    }


    public void error(TCPConnection connection, ErrorSource source, Throwable throwable) {
        final int nextIndex = (handlerIndex + 1);
        pipeline.fireError(handlersShapshot, nextIndex, connection, source, throwable);
    }

    public void error(ErrorSource source, Throwable throwable) {
        this.error(connection, source, throwable);
    }

}
