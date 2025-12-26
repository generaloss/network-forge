package generaloss.networkforge.tcp.handler;

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

        final EventHandleContext context = new EventHandleContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setIndex(indexFrom);

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

    public void fireOnConnectNext(EventHandlerLayer current, TCPConnection connection) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        this.fireOnConnect(nextIndex, connection);
    }

    public void fireOnConnect(TCPConnection connection) {
        this.fireOnConnect(0, connection);
    }


    public void fireOnDisconnect(int indexFrom, TCPConnection connection, CloseReason reason, Exception e) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventHandleContext context = new EventHandleContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setIndex(indexFrom);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                final boolean result = handler.handleDisconnect(context, reason, e);
                if(!result)
                    break;

            } catch (Throwable onDisconnectThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.DISCONNECT_HANDLER, onDisconnectThrowable);
                break;
            }
        }
    }

    public void fireOnDisconnectNext(EventHandlerLayer current, TCPConnection connection, CloseReason reason, Exception e) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        this.fireOnDisconnect(nextIndex, connection, reason, e);
    }

    public void fireOnDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        this.fireOnDisconnect(0, connection, reason, e);
    }


    public void fireOnReceive(int indexFrom, TCPConnection connection, byte[] data) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventHandleContext context = new EventHandleContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setIndex(indexFrom);

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

    public void fireOnReceiveNext(EventHandlerLayer current, TCPConnection connection, byte[] data) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        this.fireOnReceive(nextIndex, connection, data);
    }

    public void fireOnReceive(TCPConnection connection, byte[] data) {
        this.fireOnReceive(0, connection, data);
    }


    public byte[] fireOnSend(int indexFrom, TCPConnection connection, byte[] data) {
        if(indexFrom < 0)
            return data;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return data;

        final EventHandleContext context = new EventHandleContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setIndex(indexFrom);

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

    public byte[] fireOnSendNext(EventHandlerLayer current, TCPConnection connection, byte[] data) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        return this.fireOnSend(nextIndex, connection, data);
    }

    public byte[] fireOnSend(TCPConnection connection, byte[] data) {
        return this.fireOnSend(0, connection, data);
    }


    public void fireOnError(int indexFrom, TCPConnection connection, ErrorSource source, Throwable throwable) {
        if(indexFrom < 0)
            return;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return;

        final EventHandleContext context = new EventHandleContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setIndex(indexFrom);

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

    public void fireOnErrorNext(EventHandlerLayer current, TCPConnection connection, ErrorSource source, Throwable throwable) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        this.fireOnError(nextIndex, connection, source, throwable);
    }

    public void fireOnError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        this.fireOnError(0, connection, source, throwable);
    }

}