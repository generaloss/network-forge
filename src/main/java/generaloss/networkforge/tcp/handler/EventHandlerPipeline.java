package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.CloseReason;
import generaloss.networkforge.tcp.event.ErrorHandler;
import generaloss.networkforge.tcp.event.ErrorSource;

import java.util.LinkedList;

public class EventHandlerPipeline {

    private final LinkedList<EventHandlerLayer> handlersList;

    public EventHandlerPipeline() {
        this.handlersList = new LinkedList<>();
    }

    public LinkedList<EventHandlerLayer> getHandlers() {
        return handlersList;
    }

    public void addHandler(EventHandlerLayer handler) {
        handlersList.addFirst(handler);
    }

    public void addHandler(int index, EventHandlerLayer handler) {
        handlersList.add(index, handler);
    }

    public void setHandler(int index, EventHandlerLayer handler) {
        handlersList.set(index, handler);
    }

    public void removeHandler(EventHandlerLayer handler) {
        handlersList.remove(handler);
    }

    public EventHandlerLayer removeHandler(int index) {
        return handlersList.remove(index);
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


    public void fireOnReceive(int indexFrom, TCPConnection connection, byte[] byteArray) {
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
                final boolean result = handler.handleReceive(context, byteArray);
                if(!result)
                    break;

            } catch (Throwable onReceiveThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.RECEIVE_HANDLER, onReceiveThrowable);
                break;
            }
        }
    }

    public void fireOnReceiveNext(EventHandlerLayer current, TCPConnection connection, byte[] byteArray) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        this.fireOnReceive(nextIndex, connection, byteArray);
    }

    public void fireOnReceive(TCPConnection connection, byte[] byteArray) {
        this.fireOnReceive(0, connection, byteArray);
    }


    public byte[] fireOnSend(int indexFrom, TCPConnection connection, byte[] byteArray) {
        if(indexFrom < 0)
            return byteArray;

        final int length = handlersList.size();
        if(length == 0 || indexFrom >= length)
            return byteArray;

        final EventHandleContext context = new EventHandleContext(this, connection);
        for(int i = indexFrom; i < length; i++) {
            context.setIndex(indexFrom);

            try {
                final EventHandlerLayer handler = handlersList.get(i);
                byteArray = handler.handleSend(context, byteArray);
                if(byteArray == null)
                    break;

            } catch (Throwable onReceiveThrowable) {
                this.fireOnError(indexFrom, connection, ErrorSource.RECEIVE_HANDLER, onReceiveThrowable);
                break;
            }
        }
        return byteArray;
    }

    public byte[] fireOnSendNext(EventHandlerLayer current, TCPConnection connection, byte[] byteArray) {
        final int currentIndex = handlersList.indexOf(current);
        if(currentIndex == -1)
            throw new IllegalStateException("Not found ConnectionHandler '" + current.getClass().getSimpleName() + "'");

        final int nextIndex = (currentIndex + 1);
        return this.fireOnSend(nextIndex, connection, byteArray);
    }

    public byte[] fireOnSend(TCPConnection connection, byte[] byteArray) {
        return this.fireOnSend(0, connection, byteArray);
    }


    private void fireOnError(int indexFrom, TCPConnection connection, ErrorSource source, Throwable throwable) {
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
                ErrorHandler.printErrorCatch(connection, ErrorSource.ERROR_HANDLER, onErrorThrowable);
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