package generaloss.networkforge.processor;

import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.TCPCloseReason;
import generaloss.networkforge.tcp.listener.TCPErrorSource;
import generaloss.networkforge.tcp.processor.TCPConnectionProcessor;

public class ClientSSLProcessor implements TCPConnectionProcessor {

    @Override
    public boolean onConnect(TCPConnection connection) {
        return true;
    }

    @Override
    public boolean onDisconnect(TCPConnection connection, TCPCloseReason reason, Exception e) {
        return true;
    }

    @Override
    public boolean onReceive(TCPConnection connection, byte[] byteArray) {
        return true;
    }

    @Override
    public boolean onError(TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        return true;
    }

}