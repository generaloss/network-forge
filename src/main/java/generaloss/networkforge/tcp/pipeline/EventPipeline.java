package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class EventPipeline extends EventHandlerRegistry {

    private final ListenersHolder target;

    public EventPipeline(ListenersHolder target) {
        this.target = target;
    }

    public ListenersHolder getTarget() {
        return target;
    }


    private boolean isNoHandlersFor(EventHandler[] handlers, int index) {
        return (
            handlers.length == 0 ||
            index < 0 || index >= handlers.length
        );
    }
    

    public void fireConnect(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");

        if(this.isNoHandlersFor(handlers, handlerIndexFrom)) {
            target.invokeConnect(connection);
            return;
        }

        final EventInvocationContext context = new EventInvocationContext(this, connection, handlers);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeConnect()
        );
    }
    
    public void fireConnect(TCPConnection connection) {
        this.fireConnect(super.getHandlers(), 0, connection);
    }
    

    public void fireDisconnect(EventHandler[] handlers, int handlerIndexFrom,
                               TCPConnection connection, CloseReason reason, Exception e) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");

        if(this.isNoHandlersFor(handlers, handlerIndexFrom)) {
            target.invokeDisconnect(connection, reason, e);
            return;
        }

        final EventInvocationContext context = new EventInvocationContext(this, connection, handlers);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeDisconnect(reason, e)
        );
    }

    public void fireDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        this.fireDisconnect(super.getHandlers(), 0, connection, reason, e);
    }

    
    public void fireReceive(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection, byte[] data) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");
        if(data == null)
            throw new RuntimeException("Argument 'data' cannot be null");

        if(this.isNoHandlersFor(handlers, handlerIndexFrom)) {
            target.invokeReceive(connection, data);
            return;
        }

        final EventInvocationContext context = new EventInvocationContext(this, connection, handlers);
        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeReceive(data)
        );
    }

    public void fireReceive(TCPConnection connection, byte[] data) {
        this.fireReceive(super.getHandlers(), 0, connection, data);
    }


    public void fireError(EventHandler[] handlers, int handlerIndexFrom,
                          TCPConnection connection, ErrorSource source, Throwable throwable) {
        // Argument 'connection' can be null

        if(this.isNoHandlersFor(handlers, handlerIndexFrom)) {
            target.invokeError(connection, source, throwable);
            return;
        }

        final EventInvocationContext context = new EventInvocationContext(this, connection, handlers);

        do {
            context.setHandlerIndex(handlerIndexFrom++);
        } while (
            context.invokeError(source, throwable)
        );
    }

    public void fireError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        this.fireError(super.getHandlers(), 0, connection, source, throwable);
    }

    
    public boolean fireSend(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection, byte[] data) {
        if(connection == null)
            throw new RuntimeException("Argument 'connection' cannot be null");
        if(data == null)
            throw new RuntimeException("Argument 'data' cannot be null");

        target.invokeSend(connection, data);

        if(this.isNoHandlersFor(handlers, handlerIndexFrom))
            return connection.sendDirect(data);

        final EventInvocationContext context = new EventInvocationContext(this, connection, handlers);
        int index = handlerIndexFrom;
        do {
            context.setHandlerIndex(index);

            try {
                final EventHandler handler = handlers[index];
                data = handler.handleSend(context, data);
                if(data == null)
                    return false;

            } catch(Throwable t) {
                this.fireError(handlers, index, connection, ErrorSource.SEND_HANDLER, t);
            }

            index--;
        }
        while(index != -1);

        return connection.sendDirect(data);
    }
    
    public boolean fireSend(TCPConnection connection, byte[] data) {
        final EventHandler[] handlers = super.getHandlers();
        final int lastHandlerIndex = (handlers.length - 1);
        return this.fireSend(handlers, lastHandlerIndex, connection, data);
    }

    public boolean fireSend(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);
        return this.fireSend(handlers, handlerIndexFrom, connection, byteArray);
    }

    public boolean fireSend(TCPConnection connection, ByteBuffer buffer) {
        final EventHandler[] handlers = super.getHandlers();
        final int lastHandlerIndex = (handlers.length - 1);
        return this.fireSend(handlers, lastHandlerIndex, connection, buffer);
    }

    public boolean fireSend(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection, String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.fireSend(handlers, handlerIndexFrom, connection, string.getBytes());
    }

    public boolean fireSend(TCPConnection connection, String string) {
        final EventHandler[] handlers = super.getHandlers();
        final int lastHandlerIndex = (handlers.length - 1);
        return this.fireSend(handlers, lastHandlerIndex, connection, string);
    }

    public boolean fireSend(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.fireSend(handlers, handlerIndexFrom, connection, byteArray);

        } catch (IOException e) {
            this.fireError(handlers, handlerIndexFrom, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(TCPConnection connection, BinaryStreamWriter streamWriter) {
        final EventHandler[] handlers = super.getHandlers();
        final int lastHandlerIndex = (handlers.length - 1);
        return this.fireSend(handlers, lastHandlerIndex, connection, streamWriter);
    }

    public boolean fireSend(EventHandler[] handlers, int handlerIndexFrom,
                            TCPConnection connection, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.fireSend(handlers, handlerIndexFrom, connection, byteArray);

        } catch (IOException e) {
            this.fireError(handlers, handlerIndexFrom, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(TCPConnection connection, NetPacket<?> packet) {
        final EventHandler[] handlers = super.getHandlers();
        final int lastHandlerIndex = (handlers.length - 1);
        return this.fireSend(handlers, lastHandlerIndex, connection, packet);
    }

}