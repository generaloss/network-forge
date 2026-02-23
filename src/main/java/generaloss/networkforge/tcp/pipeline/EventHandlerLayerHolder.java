package generaloss.networkforge.tcp.pipeline;

import java.util.LinkedList;

public class EventHandlerLayerHolder extends LinkedList<EventHandlerLayer> {

    public int count() {
        return super.size();
    }

}
