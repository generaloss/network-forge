package generaloss.networkforge.tcp.options;

import java.net.Socket;

public class TCPConnectionOptions extends SocketOptions {

    public TCPConnectionOptions(Socket socket) {
        super(socket);
    }


    private int maxReadFrameSize;

    public int getMaxReadFrameSize() {
        return maxReadFrameSize;
    }

    public TCPConnectionOptions setMaxReadFrameSize(int maxReadFrameSize) {
        if(maxReadFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxFrameSizeRead' must be > 0");

        this.maxReadFrameSize = maxReadFrameSize;
        return this;
    }


    private int maxWriteFrameSize;

    public int getMaxWriteFrameSize() {
        return maxWriteFrameSize;
    }

    public TCPConnectionOptions setMaxWriteFrameSize(int maxWriteFrameSize) {
        if(maxWriteFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxWriteFrameSize' must be > 0");

        this.maxWriteFrameSize = maxWriteFrameSize;
        return this;
    }


    public TCPConnectionOptions setMaxFrameSize(int maxFrameSize) {
        if(maxFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxFrameSize' must be > 0");

        this.maxReadFrameSize = maxFrameSize;
        this.maxWriteFrameSize = maxFrameSize;
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


    private int frameBufferSizeUpperBound;

    public int getFrameBufferSizeUpperBound() {
        return frameBufferSizeUpperBound;
    }

    /** Used only by FramedTCPConnectionCodec.
      * Buffer will not be narrowed while value is set to 0. */
    public TCPConnectionOptions setFrameBufferSizeUpperBound(int frameBufferSizeUpperBound) {
        if(frameBufferSizeUpperBound < 0)
            throw new IllegalArgumentException("Argument 'frameBufferSizeUpperBound' must be >= 0");

        this.frameBufferSizeUpperBound = frameBufferSizeUpperBound;
        return this;
    }


    @Override
    public String toString() {
        return TCPConnectionOptions.class.getSimpleName() + "{" +
            "MAX_READ_FRAME_SIZE=" + maxReadFrameSize +
            ", MAX_WRITE_FRAME_SIZE=" + maxWriteFrameSize +
            ", CLOSE_ON_FRAME_SIZE_LIMIT=" + closeOnFrameSizeLimit +
            ", FRAME_BUFFER_SIZE_UPEER_BOUND=" + frameBufferSizeUpperBound +
            ", " + super.optionsToString() + "}";
    }

}
