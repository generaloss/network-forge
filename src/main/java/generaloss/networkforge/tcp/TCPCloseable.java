package generaloss.networkforge.tcp;

import generaloss.networkforge.NetCloseCause;

@FunctionalInterface
public interface TCPCloseable {

    void close(TCPConnection connection, NetCloseCause netCloseCause, Exception e);

}
