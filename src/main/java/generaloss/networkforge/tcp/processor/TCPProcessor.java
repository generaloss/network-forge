package generaloss.networkforge.tcp.processor;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.event.*;

public interface TCPProcessor {

    void onAdded(EventDispatcher eventDispatcher);

    boolean onConnect(TCPConnection connection);

    boolean onDisconnect(TCPConnection connection, CloseReason reason, Exception e);

    boolean onReceive(TCPConnection connection, byte[] byteArray);

    boolean onError(TCPConnection connection, ErrorSource source, Throwable throwable);

}
