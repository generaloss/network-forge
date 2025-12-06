package generaloss.networkforge.tcp.options;

import java.net.Socket;

public class TCPConnectionOptions extends SocketOptions {

    public TCPConnectionOptions(Socket socket) {
        super(socket);
    }


    private int maxFrameSizeRead;

    public int getMaxFrameSizeRead() {
        return maxFrameSizeRead;
    }

    public TCPConnectionOptions setMaxFrameSizeRead(int maxFrameSizeRead) {
        if(maxFrameSizeRead < 1)
            throw new IllegalArgumentException("Argument 'maxFrameSizeRead' must be > 0");

        this.maxFrameSizeRead = maxFrameSizeRead;
        return this;
    }


    private int maxFrameSizeWrite;

    public int getMaxFrameSizeWrite() {
        return maxFrameSizeWrite;
    }

    public TCPConnectionOptions setMaxFrameSizeWrite(int maxFrameSizeWrite) {
        if(maxFrameSizeWrite < 1)
            throw new IllegalArgumentException("Argument 'maxFrameSizeWrite' must be > 0");

        this.maxFrameSizeWrite = maxFrameSizeWrite;
        return this;
    }


    public TCPConnectionOptions setMaxFrameSize(int maxFrameSize) {
        if(maxFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxFrameSize' must be > 0");

        this.maxFrameSizeRead = maxFrameSize;
        this.maxFrameSizeWrite = maxFrameSize;
        return this;
    }


    private boolean closeOnFrameSizeLimit;

    public boolean isCloseOnFrameSizeLimit() {
        return closeOnFrameSizeLimit;
    }

    public TCPConnectionOptions setCloseOnFrameSizeLimit(boolean closeOnFrameSizeLimit) {
        this.closeOnFrameSizeLimit = closeOnFrameSizeLimit;
        return this;
    }


    @Override
    public String toString() {
        return TCPConnectionOptions.class.getSimpleName() + "{" +
            "MAX_FRAME_SIZE_READ=" + maxFrameSizeRead +
            ", MAX_FRAME_SIZE_WRITE=" + maxFrameSizeWrite +
            ", CLOSE_ON_FRAME_SIZE_LIMIT=" + closeOnFrameSizeLimit +
            ", " + super.optionsToString() + "}";
    }

}
