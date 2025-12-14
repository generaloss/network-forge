package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.tcp.TCPConnection;

public class PipelineContext {

    private final TCPConnection connection;
    private int index;

    public PipelineContext(TCPConnection connection) {
        this.connection = connection;
    }

    public TCPConnection connection() {
        return connection;
    }




}
