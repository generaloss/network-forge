package generaloss.networkforge.tcp.options;

public class TCPSocketOptionsHolder {

    // boolean TCP_NODELAY
    private Boolean tcpNoDelay;

    public Boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public TCPSocketOptionsHolder setTcpNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }


    // boolean TCP_QUICKACK
    private Boolean tcpQuickAck;

    public Boolean isTcpQuickAck() {
        return tcpQuickAck;
    }

    public TCPSocketOptionsHolder setTcpQuickAck(Boolean tcpQuickAck) {
        this.tcpQuickAck = tcpQuickAck;
        return this;
    }


    // int TCP_KEEPIDLE
    private Integer tcpKeepIdle;

    public Integer getTcpKeepIdle() {
        return tcpKeepIdle;
    }

    public TCPSocketOptionsHolder setTcpKeepIdle(Integer tcpKeepIdle) {
        this.tcpKeepIdle = tcpKeepIdle;
        return this;
    }


    // int TCP_KEEPINTERVAL
    private Integer tcpKeepInterval;

    public Integer getTcpKeepInterval() {
        return tcpKeepInterval;
    }

    public TCPSocketOptionsHolder setTcpKeepInterval(Integer tcpKeepInterval) {
        this.tcpKeepInterval = tcpKeepInterval;
        return this;
    }


    // int TCP_KEEPCOUNT
    private Integer tcpKeepCount;

    public Integer getTcpKeepCount() {
        return tcpKeepCount;
    }

    public TCPSocketOptionsHolder setTcpKeepCount(Integer tcpKeepCount) {
        this.tcpKeepCount = tcpKeepCount;
        return this;
    }


    // int IP_TOS
    private Integer trafficClass;

    public Integer getTrafficClass() {
        return trafficClass;
    }

    public TCPSocketOptionsHolder setTrafficClass(Integer trafficClass) {
        this.trafficClass = trafficClass;
        return this;
    }


    // boolean SO_OOBINLINE
    private Boolean oobInline;

    public Boolean isOobInline() {
        return oobInline;
    }

    public TCPSocketOptionsHolder setOobInline(Boolean oobInline) {
        this.oobInline = oobInline;
        return this;
    }


    // boolean SO_KEEPALIVE
    private Boolean keepAlive;

    public Boolean isKeepAlive() {
        return keepAlive;
    }

    public TCPSocketOptionsHolder setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }


    // boolean SO_REUSEADDR
    private Boolean reuseAddress;

    public Boolean isReuseAddress() {
        return reuseAddress;
    }

    public TCPSocketOptionsHolder setReuseAddress(Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return this;
    }


    // boolean SO_REUSEPORT
    private Boolean reusePort;

    public Boolean isReusePort() {
        return reusePort;
    }

    public TCPSocketOptionsHolder setReusePort(Boolean reusePort) {
        this.reusePort = reusePort;
        return this;
    }


    // int SO_RCVBUF
    private Integer receiveBufferSize;

    public Integer getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public TCPSocketOptionsHolder setReceiveBufferSize(Integer receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }


    // int SO_SNDBUF
    private Integer sendBufferSize;

    public Integer getSendBufferSize() {
        return sendBufferSize;
    }

    public TCPSocketOptionsHolder setSendBufferSize(Integer sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return this;
    }


    // int SO_LINGER
    private Integer linger;

    public Integer getLinger() {
        return linger;
    }

    public TCPSocketOptionsHolder setLinger(Integer linger) {
        this.linger = linger;
        return this;
    }


    public void applyPreConnectOn(TCPSocketOptions options) {
        if(trafficClass != null) options.setTrafficClass(trafficClass);
        if(reuseAddress != null) options.setReuseAddress(reuseAddress);
        if(reusePort != null) options.setReusePort(reusePort);
        if(receiveBufferSize != null) options.setReceiveBufferSize(receiveBufferSize);
        if(sendBufferSize != null) options.setSendBufferSize(sendBufferSize);
    }

    public void applyPostConnectOn(TCPSocketOptions options) {
        if(tcpNoDelay != null) options.setTcpNoDelay(tcpNoDelay);
        if(tcpQuickAck != null) options.setTcpQuickAck(tcpQuickAck);
        if(tcpKeepIdle != null) options.setTcpKeepIdle(tcpKeepIdle);
        if(tcpKeepInterval != null) options.setTcpKeepInterval(tcpKeepInterval);
        if(tcpKeepCount != null) options.setTcpKeepCount(tcpKeepCount);
        if(oobInline != null) options.setOOBInline(oobInline);
        if(keepAlive != null) options.setKeepAlive(keepAlive);
        if(linger != null) options.setLinger(linger);
    }

    @Override
    public String toString() {
        return TCPSocketOptionsHolder.class.getSimpleName() + "{" + this.optionsToString() + "}";
    }

    protected String optionsToString() {
        return "TCP_NODELAY=" + tcpNoDelay +
            ", TCP_QUICKACK=" + tcpQuickAck +
            ", TCP_KEEPIDLE=" + tcpKeepIdle +
            ", TCP_KEEPINTERVAL=" + tcpKeepInterval +
            ", TCP_KEEPCOUNT=" + tcpKeepCount +
            ", IP_TOS=" + trafficClass +
            ", SO_OOBINLINE=" + oobInline +
            ", SO_KEEPALIVE=" + keepAlive +
            ", SO_REUSEADDR=" + reuseAddress +
            ", SO_REUSEPORT=" + reusePort +
            ", SO_RCVBUF=" + receiveBufferSize +
            ", SO_SNDBUF=" + sendBufferSize +
            ", SO_LINGER=" + linger;
    }

}
