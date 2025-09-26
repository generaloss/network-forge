package generaloss.networkforge.tcp.options;

import generaloss.networkforge.tcp.PacketTCPConnection;

import java.net.Socket;

public class TCPConnectionOptions extends TCPSocketOptions {

    public TCPConnectionOptions(Socket socket) {
        super(socket);
    }


    private int maxPacketSizeRead = PacketTCPConnection.DEFAULT_MAX_PACKET_SIZE;

    public int getMaxPacketSizeRead() {
        return maxPacketSizeRead;
    }

    public TCPConnectionOptions setMaxPacketSizeRead(int maxPacketSizeRead) {
        if(maxPacketSizeRead < 1)
            throw new IllegalArgumentException("maxPacketSizeRead must be > 0");
        this.maxPacketSizeRead = maxPacketSizeRead;
        return this;
    }


    private int maxPacketSizeWrite = PacketTCPConnection.DEFAULT_MAX_PACKET_SIZE;

    public int getMaxPacketSizeWrite() {
        return maxPacketSizeWrite;
    }

    public TCPConnectionOptions setMaxPacketSizeWrite(int maxPacketSizeWrite) {
        if(maxPacketSizeWrite < 1)
            throw new IllegalArgumentException("maxPacketSizeWrite must be > 0");
        this.maxPacketSizeWrite = maxPacketSizeWrite;
        return this;
    }


    public TCPConnectionOptions setMaxPacketSize(int maxPacketSize) {
        if(maxPacketSize < 1)
            throw new IllegalArgumentException("maxPacketSize must be > 0");
        this.maxPacketSizeRead = maxPacketSize;
        this.maxPacketSizeWrite = maxPacketSize;
        return this;
    }


    private boolean closeOnPacketLimit = true;

    public boolean isCloseOnPacketLimit() {
        return closeOnPacketLimit;
    }

    public TCPConnectionOptions setCloseOnPacketLimit(boolean closeOnPacketLimit) {
        this.closeOnPacketLimit = closeOnPacketLimit;
        return this;
    }


    @Override
    public String toString() {
        return TCPConnectionOptions.class.getSimpleName() + "{" +
            "MAX_PACKET_SIZE_READ=" + maxPacketSizeRead +
            ", MAX_PACKET_SIZE_WRITE=" + maxPacketSizeWrite +
            ", CLOSE_ON_PACKET_LIMIT=" + closeOnPacketLimit +
            super.optionsToString() + "}";
    }

}
