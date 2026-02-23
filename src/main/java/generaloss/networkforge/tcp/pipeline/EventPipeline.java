package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class EventPipeline {

    private final ListenersHolder target;
    private final EventHandlerLayerHolder handlers;

    public EventPipeline(ListenersHolder target) {
        this.target = target;
        this.handlers = new EventHandlerLayerHolder();
    }

    public ListenersHolder getTarget() {
        return target;
    }

    public EventHandlerLayerHolder getHandlers() {
        return handlers;
    }


    private boolean isNoHandlersFor(int handlerIndex) {
        return (
            handlers.isEmpty() ||
            handlerIndex < 0 || handlerIndex >= handlers.count()
        );
    }
    

    public void fireConnect(int handlerIndexFrom, TCPConnection connection) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");

        if(this.isNoHandlersFor(handlerIndexFrom)) {
            target.invokeConnect(connection);
            return;
        }

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeConnect()
        );
    }
    
    public void fireConnect(TCPConnection connection) {
        this.fireConnect(0, connection);
    }
    

    public void fireDisconnect(int handlerIndexFrom, TCPConnection connection, CloseReason reason, Exception e) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");

        if(this.isNoHandlersFor(handlerIndexFrom)) {
            target.invokeDisconnect(connection, reason, e);
            return;
        }

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeDisconnect(reason, e)
        );
    }

    public void fireDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        this.fireDisconnect(0, connection, reason, e);
    }

    
    public void fireReceive(int handlerIndexFrom, TCPConnection connection, byte[] data) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");
        if(data == null)
            throw new RuntimeException("Argument 'data' cannot be null");

        if(this.isNoHandlersFor(handlerIndexFrom)) {
            target.invokeReceive(connection, data);
            return;
        }

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeReceive(data)
        );
    }

    public void fireReceive(TCPConnection connection, byte[] data) {
        this.fireReceive(0, connection, data);
    }


    public void fireError(int handlerIndexFrom, TCPConnection connection, ErrorSource source, Throwable throwable) {
        // Argument 'connection' can be null

        if(this.isNoHandlersFor(handlerIndexFrom)) {
            target.invokeError(connection, source, throwable);
            return;
        }

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeError(source, throwable)
        );
    }

    public void fireError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        this.fireError(0, connection, source, throwable);
    }

    
    public boolean fireSend(int handlerIndexFrom, TCPConnection connection, byte[] data) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");
        if(data == null)
            throw new RuntimeException("Argument 'data' cannot be null");

        target.invokeSend(connection, data);

        if(this.isNoHandlersFor(handlerIndexFrom))
            return connection.sendDirect(data);

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        do {
            context.setHandlerIndex(handlerIndexFrom);

            try {
                final EventHandlerLayer handler = handlers.get(handlerIndexFrom);
                data = handler.handleSend(context, data);
                if(data == null)
                    return false;

            } catch(Throwable t) {
                this.fireError(handlerIndexFrom, connection, ErrorSource.SEND_HANDLER, t);
            }

            handlerIndexFrom--;
        }
        while(handlerIndexFrom != -1);

        return connection.sendDirect(data);
    }
    
    public boolean fireSend(TCPConnection connection, byte[] data) {
        final int lastHandlerIndex = (handlers.size() - 1);
        return this.fireSend(lastHandlerIndex, connection, data);
    }

    public boolean fireSend(int handlerIndexFrom, TCPConnection connection, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);
        return this.fireSend(handlerIndexFrom, connection, byteArray);
    }

    public boolean fireSend(TCPConnection connection, ByteBuffer buffer) {
        final int lastHandlerIndex = (handlers.size() - 1);
        return this.fireSend(lastHandlerIndex, connection, buffer);
    }

    public boolean fireSend(int handlerIndexFrom, TCPConnection connection, String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.fireSend(handlerIndexFrom, connection, string.getBytes());
    }

    public boolean fireSend(TCPConnection connection, String string) {
        final int lastHandlerIndex = (handlers.size() - 1);
        return this.fireSend(lastHandlerIndex, connection, string);
    }

    public boolean fireSend(int handlerIndexFrom, TCPConnection connection, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.fireSend(handlerIndexFrom, connection, byteArray);

        } catch (IOException e) {
            this.fireError(handlerIndexFrom, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(TCPConnection connection, BinaryStreamWriter streamWriter) {
        final int lastHandlerIndex = (handlers.size() - 1);
        return this.fireSend(lastHandlerIndex, connection, streamWriter);
    }

    public boolean fireSend(int handlerIndexFrom, TCPConnection connection, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.fireSend(handlerIndexFrom, connection, byteArray);

        } catch (IOException e) {
            this.fireError(handlerIndexFrom, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(TCPConnection connection, NetPacket<?> packet) {
        final int lastHandlerIndex = (handlers.size() - 1);
        return this.fireSend(lastHandlerIndex, connection, packet);
    }

}