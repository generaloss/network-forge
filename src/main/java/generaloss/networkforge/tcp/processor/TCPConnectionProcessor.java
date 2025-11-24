package generaloss.networkforge.tcp.processor;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.*;

public interface TCPConnectionProcessor {

    boolean onConnect(TCPConnection connection);

    boolean onDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e);

    boolean onReceive(TCPConnection connection, byte[] byteArray);

    boolean onError(TCPConnection connection, TCPErrorSource source, Throwable throwable);

}
