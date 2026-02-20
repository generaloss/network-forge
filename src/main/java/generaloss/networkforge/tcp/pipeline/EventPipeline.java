package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;

import java.util.LinkedList;

public class EventPipeline {

    private final ListenersHolder target;
    private final LinkedList<EventHandlerLayer> handlers;

    public EventPipeline(ListenersHolder target) {
        this.target = target;
        this.handlers = new LinkedList<>();
    }

    public ListenersHolder getTarget() {
        return target;
    }

    public LinkedList<EventHandlerLayer> getHandlers() {
        return handlers;
    }


    public EventHandlerLayer getHandler(int index) {
        return handlers.get(index);
    }

    public int getHandlersCount() {
        return handlers.size();
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
    }


    public void addHandlerFirst(EventHandlerLayer handler) {
        handlers.addFirst(handler);
    }

    public void addHandlerLast(EventHandlerLayer handler) {
        handlers.addLast(handler);
    }

    public void addHandler(int index, EventHandlerLayer handler) {
        handlers.add(index, handler);
    }

    public void setHandler(int index, EventHandlerLayer handler) {
        handlers.set(index, handler);
    }

    public boolean containsHandler(EventHandlerLayer handler) {
        return handlers.contains(handler);
    }

    public void removeHandler(EventHandlerLayer handler) {
        handlers.remove(handler);
    }

    public EventHandlerLayer removeHandler(int index) {
        return handlers.remove(index);
    }

    public void clearHandlers() {
        handlers.clear();
    }


    public void fireConnect(int handlerIndexFrom, TCPConnection connection) {
        if(handlers.isEmpty() || handlerIndexFrom < 0 || handlerIndexFrom >= handlers.size()) {
            target.handleConnect(connection);
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
        if(handlers.isEmpty() || handlerIndexFrom < 0 || handlerIndexFrom >= handlers.size()) {
            target.handleDisconnect(connection, reason, e);
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
        if(data == null)
            throw new RuntimeException("Argument 'data' cannot be null");

        if(handlers.isEmpty() || handlerIndexFrom < 0 || handlerIndexFrom >= handlers.size()) {
            target.handleReceive(connection, data);
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

    
    public boolean fireSend(int handlerIndexFrom, TCPConnection connection, byte[] data) {
        if(data == null)
            throw new RuntimeException("Argument 'data' cannot be null");

        if(handlers.isEmpty() || handlerIndexFrom < 0 || handlerIndexFrom >= handlers.size())
            return connection.sendDirect(data);

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        do {
            if(handlerIndexFrom == -1)
                return connection.sendDirect(context.getSendProcessedData()); // break

            context.setHandlerIndex(handlerIndexFrom--);
        } while (
            context.invokeSend(data)
        );
        return false;
    }
    
    public void fireSend(TCPConnection connection, byte[] data) {
        final int lastHandlerIndex = (handlers.size() - 1);
        this.fireSend(lastHandlerIndex, connection, data);
    }

    
    public void fireError(int handlerIndexFrom, TCPConnection connection, ErrorSource source, Throwable throwable) {
        if(handlers.isEmpty() || handlerIndexFrom < 0 || handlerIndexFrom >= handlers.size()) {
            target.handleError(connection, source, throwable);
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

}