package generaloss.networkforge.tcp.options;

public class TCPConnectionOptionsHolder extends SocketOptionsHolder {

    public static final int DEFAULT_MAX_FRAME_SIZE = (8 * 1024 * 1024); // 8 Mb.  (Integer.MAX_VALUE ≈ 2 Gb)
    public static final int DEFAULT_FRAME_BUFFER_SIZE_UPPER_BOUND = (2 * 1024 * 1024); // 2 Mb.


    private int maxReadFrameSize = DEFAULT_MAX_FRAME_SIZE;

    public int getMaxReadFrameSize() {
        return maxReadFrameSize;
    }

    public TCPConnectionOptionsHolder setMaxReadFrameSize(int maxReadFrameSize) {
        if(maxReadFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxReadFrameSize' must be > 0");

        this.maxReadFrameSize = maxReadFrameSize;
        return this;
    }


    private int maxWriteFrameSize = DEFAULT_MAX_FRAME_SIZE;

    public int getMaxWriteFrameSize() {
        return maxWriteFrameSize;
    }

    public TCPConnectionOptionsHolder setMaxWriteFrameSize(int maxWriteFrameSize) {
        if(maxWriteFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxWriteFrameSize' must be > 0");

        this.maxWriteFrameSize = maxWriteFrameSize;
        return this;
    }


    public TCPConnectionOptionsHolder setMaxFrameSize(int maxFrameSize) {
        if(maxFrameSize < 1)
            throw new IllegalArgumentException("Argument 'maxFrameSize' must be > 0");

        this.maxReadFrameSize = maxFrameSize;
        this.maxWriteFrameSize = maxFrameSize;
        return this;
    }


    private boolean closeOnFrameReadSizeExceed = true;

    public boolean isCloseOnFrameReadSizeExceed() {
        return closeOnFrameReadSizeExceed;
    }

    public TCPConnectionOptionsHolder setCloseOnFrameReadSizeExceed(boolean closeOnFrameReadSizeExceed) {
        this.closeOnFrameReadSizeExceed = closeOnFrameReadSizeExceed;
        return this;
    }


    private int frameBufferSizeUpperBound = DEFAULT_FRAME_BUFFER_SIZE_UPPER_BOUND;

    public int getFrameBufferSizeUpperBound() {
        return frameBufferSizeUpperBound;
    }

    /** Used only by FramedTCPConnectionCodec.
      * Buffer will not be narrowed while value is set to 0. */
    public TCPConnectionOptionsHolder setFrameBufferSizeUpperBound(int frameBufferSizeUpperBound) {
        if(frameBufferSizeUpperBound < 0)
            throw new IllegalArgumentException("Argument 'frameBufferSizeUpperBound' must be >= 0");

        this.frameBufferSizeUpperBound = frameBufferSizeUpperBound;
        return this;
    }


    public void copyTo(TCPConnectionOptions options) {
        options.setMaxReadFrameSize(maxReadFrameSize);
        options.setMaxWriteFrameSize(maxWriteFrameSize);
        options.setCloseOnFrameReadSizeExceed(closeOnFrameReadSizeExceed);
        options.setFrameBufferSizeUpperBound(frameBufferSizeUpperBound);
    }


    @Override
    public String toString() {
        return TCPConnectionOptionsHolder.class.getSimpleName() + "{" +
            "MAX_READ_FRAME_SIZE=" + maxReadFrameSize +
            ", MAX_WRITE_FRAME_SIZE=" + maxWriteFrameSize +
            ", CLOSE_ON_FRAME_SIZE_LIMIT=" + closeOnFrameReadSizeExceed +
            ", FRAME_BUFFER_SIZE_UPEER_BOUND=" + frameBufferSizeUpperBound +
            ", " + super.optionsToString() + "}";
    }

}
