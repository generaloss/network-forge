package generaloss.networkforge.tcp.event;

import generaloss.networkforge.tcp.TCPConnection;

@FunctionalInterface
public interface CloseCallback {

    void onClose(TCPConnection connection, CloseReason reason, Exception e);

}
