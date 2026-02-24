package generaloss.networkforge.tcp.pipeline;

import java.util.ArrayList;
import java.util.List;

class EventHandlerRegistry {

    private final List<EventHandler> handlersList;
    private final Object handlersLock;

    private volatile EventHandler[] handlers;

    public EventHandlerRegistry() {
        this.handlersList = new ArrayList<>();
        this.handlersLock = new Object();

        this.handlers = new EventHandler[0];
    }


    public EventHandler[] getHandlers() {
        return handlers;
    }

    private void commit() {
        handlers = handlersList.toArray(EventHandler[]::new);
    }


    public void addHandlerLast(EventHandler handler) {
        synchronized(handlersLock) {
            handlersList.add(handler);
            this.commit();
        }
    }

    public void addHandlerFirst(EventHandler handler) {
        synchronized(handlersLock) {
            handlersList.add(0, handler);
            this.commit();
        }
    }

    public void addHandler(int index, EventHandler handler) {
        synchronized(handlersLock) {
            handlersList.add(index, handler);
            this.commit();
        }
    }

    public EventHandler getHandler(int index) {
        synchronized(handlersLock) {
            return handlersList.get(index);
        }
    }

    public void removeHandler(int index) {
        synchronized(handlersLock) {
            handlersList.remove(index);
            this.commit();
        }
    }

    public void removeHandler(EventHandler handler) {
        synchronized(handlersLock) {
            handlersList.remove(handler);
            this.commit();
        }
    }

    public void clearHandlers() {
        synchronized(handlersLock) {
            handlersList.clear();
            this.commit();
        }
    }

    public int size() {
        synchronized(handlersLock) {
            return handlersList.size();
        }
    }

}
