package generaloss.networkforge.tcp.options;

import generaloss.networkforge.tcp.PacketTCPConnection;

public class TCPConnectionOptionsHolder extends SocketOptionsHolder {

    private int maxPacketSizeRead = PacketTCPConnection.DEFAULT_MAX_PACKET_SIZE;

    public int getMaxPacketSizeRead() {
        return maxPacketSizeRead;
    }

    public TCPConnectionOptionsHolder setMaxPacketSizeRead(int maxPacketSizeRead) {
        if(maxPacketSizeRead < 1)
            throw new IllegalArgumentException("Argument 'maxPacketSizeRead' must be > 0");

        this.maxPacketSizeRead = maxPacketSizeRead;
        return this;
    }


    private int maxPacketSizeWrite = PacketTCPConnection.DEFAULT_MAX_PACKET_SIZE;

    public int getMaxPacketSizeWrite() {
        return maxPacketSizeWrite;
    }

    public TCPConnectionOptionsHolder setMaxPacketSizeWrite(int maxPacketSizeWrite) {
        if(maxPacketSizeWrite < 1)
            throw new IllegalArgumentException("Argument 'maxPacketSizeWrite' must be > 0");

        this.maxPacketSizeWrite = maxPacketSizeWrite;
        return this;
    }


    public TCPConnectionOptionsHolder setMaxPacketSize(int maxPacketSize) {
        if(maxPacketSize < 1)
            throw new IllegalArgumentException("Argument 'maxPacketSize' must be > 0");

        this.maxPacketSizeRead = maxPacketSize;
        this.maxPacketSizeWrite = maxPacketSize;
        return this;
    }


    private boolean closeOnPacketLimit = true;

    public boolean isCloseOnPacketLimit() {
        return closeOnPacketLimit;
    }

    public TCPConnectionOptionsHolder setCloseOnPacketLimit(boolean closeOnPacketLimit) {
        this.closeOnPacketLimit = closeOnPacketLimit;
        return this;
    }


    public void copyTo(TCPConnectionOptions options) {
        options.setMaxPacketSizeRead(maxPacketSizeRead);
        options.setMaxPacketSizeWrite(maxPacketSizeWrite);
        options.setCloseOnPacketLimit(closeOnPacketLimit);
    }


    @Override
    public String toString() {
        return TCPConnectionOptionsHolder.class.getSimpleName() + "{" +
            "MAX_PACKET_SIZE_READ=" + maxPacketSizeRead +
            ", MAX_PACKET_SIZE_WRITE=" + maxPacketSizeWrite +
            ", CLOSE_ON_PACKET_LIMIT=" + closeOnPacketLimit +
            ", " + super.optionsToString() + "}";
    }

}
