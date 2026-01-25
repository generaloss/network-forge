package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorListener;
import generaloss.networkforge.tcp.listener.ErrorSource;

import java.util.LinkedList;

public class EventPipeline {

    private final LinkedList<EventHandlerLayer> handlersList;

    public EventPipeline() {
        this.handlersList = new LinkedList<>();
    }

    public LinkedList<EventHandlerLayer> getHandlers() {
        return handlersList;
    }

    public void addHandlerFirst(EventHandlerLayer handler) {
        handlersList.addFirst(handler);
    }

    public void addHandlerLast(EventHandlerLayer handler) {
        handlersList.addLast(handler);
    }

    public void addHandler(int index, EventHandlerLayer handler) {
        handlersList.add(index, handler);
    }

    public void setHandler(int index, EventHandlerLayer handler) {
        handlersList.set(index, handler);
    }

    public boolean containsHandler(EventHandlerLayer handler) {
        return handlersList.contains(handler);
    }

    public void removeHandler(EventHandlerLayer handler) {
        handlersList.remove(handler);
    }

    public EventHandlerLayer removeHandler(int index) {
        return handlersList.remove(index);
    }

    public void clearHandlers() {
        handlersList.clear();
    }


    public void fireOnConnect(int indexFrom, TCPConnection connection) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setPipelineStartIndex(i + 1);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                final boolean result = handler.handleConnect(context);
                if(!result)
                    break;

            } catch (Throwable onConnectThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.CONNECT_HANDLER, onConnectThrowable);
                break;
            }
        }
    }

    public void fireOnDisconnect(int indexFrom, TCPConnection connection, CloseReason reason, Exception e) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setPipelineStartIndex(i + 1);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                handler.handleDisconnect(context, reason, e);

            } catch (Throwable onDisconnectThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.DISCONNECT_HANDLER, onDisconnectThrowable);
                break;
            }
        }
    }

    public void fireOnReceive(int indexFrom, TCPConnection connection, byte[] data) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setPipelineStartIndex(i + 1);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                final boolean result = handler.handleReceive(context, data);
                if(!result)
                    break;

            } catch (Throwable onReceiveThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.RECEIVE_HANDLER, onReceiveThrowable);
                break;
            }
        }
    }

    public byte[] fireOnSend(int indexFrom, TCPConnection connection, byte[] data) {
        if(indexFrom < 0)
            return data;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return data;

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setPipelineStartIndex(i + 1);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                data = handler.handleSend(context, data);
                if(data == null)
                    break;

            } catch (Throwable onReceiveThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.RECEIVE_HANDLER, onReceiveThrowable);
                break;
            }
        }
        return data;
    }

    public void fireOnError(int indexFrom, TCPConnection connection, ErrorSource source, Throwable throwable) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventPipelineContext context = new EventPipelineContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setPipelineStartIndex(i + 1);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                final boolean result = handler.handleError(context, source, throwable);
                if(!result)
                    break;

            } catch (Throwable onErrorThrowable) {
                ErrorListener.printErrorCatch(connection, ErrorSource.ERROR_HANDLER, onErrorThrowable);
                break;
            }
        }
    }

}