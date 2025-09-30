package generaloss.networkforge.tcp.options;

import generaloss.networkforge.SocketConsumer;
import jdk.net.ExtendedSocketOptions;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/* TCP Socket Options:
boolean  TCP_NODELAY       pre-connect
boolean  TCP_QUICKACK      post-bind
int      TCP_KEEPIDLE      post-bind
int      TCP_KEEPINTERVAL  post-bind
int      TCP_KEEPCOUNT     post-bind
int      IP_TOS            pre-connect
boolean  SO_OOBINLINE      pre-connect
boolean  SO_KEEPALIVE      pre-connect
boolean  SO_REUSEADDR      pre-connect
boolean  SO_REUSEPORT      pre-bind
int      SO_RCVBUF         pre-connect
int      SO_SNDBUF         pre-connect
int      SO_LINGER         pre-connect
*/
public class SocketOptionsHolder {

    // boolean TCP_NODELAY
    private Boolean tcpNoDelay;

    public Boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public SocketOptionsHolder setTcpNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }


    // boolean TCP_QUICKACK
    private Boolean tcpQuickAck;

    public Boolean isTcpQuickAck() {
        return tcpQuickAck;
    }

    public SocketOptionsHolder setTcpQuickAck(Boolean tcpQuickAck) {
        this.tcpQuickAck = tcpQuickAck;
        return this;
    }


    // int TCP_KEEPIDLE
    private Integer tcpKeepIdle;

    public Integer getTcpKeepIdle() {
        return tcpKeepIdle;
    }

    public SocketOptionsHolder setTcpKeepIdle(Integer tcpKeepIdle) {
        this.tcpKeepIdle = tcpKeepIdle;
        return this;
    }


    // int TCP_KEEPINTERVAL
    private Integer tcpKeepInterval;

    public Integer getTcpKeepInterval() {
        return tcpKeepInterval;
    }

    public SocketOptionsHolder setTcpKeepInterval(Integer tcpKeepInterval) {
        this.tcpKeepInterval = tcpKeepInterval;
        return this;
    }


    // int TCP_KEEPCOUNT
    private Integer tcpKeepCount;

    public Integer getTcpKeepCount() {
        return tcpKeepCount;
    }

    public SocketOptionsHolder setTcpKeepCount(Integer tcpKeepCount) {
        this.tcpKeepCount = tcpKeepCount;
        return this;
    }


    // int IP_TOS (before bind)
    private Integer trafficClass;

    public Integer getTrafficClass() {
        return trafficClass;
    }

    public SocketOptionsHolder setTrafficClass(Integer trafficClass) {
        this.trafficClass = trafficClass;
        return this;
    }


    // boolean SO_OOBINLINE
    private Boolean oobInline;

    public Boolean isOOBInline() {
        return oobInline;
    }

    public SocketOptionsHolder setOOBInline(Boolean oobInline) {
        this.oobInline = oobInline;
        return this;
    }


    // boolean SO_KEEPALIVE
    private Boolean keepAlive;

    public Boolean isKeepAlive() {
        return keepAlive;
    }

    public SocketOptionsHolder setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }


    // boolean SO_REUSEADDR (before bind)
    private Boolean reuseAddress;

    public Boolean isReuseAddress() {
        return reuseAddress;
    }

    public SocketOptionsHolder setReuseAddress(Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return this;
    }


    // boolean SO_REUSEPORT (before bind)
    private Boolean reusePort;

    public Boolean isReusePort() {
        return reusePort;
    }

    public SocketOptionsHolder setReusePort(Boolean reusePort) {
        this.reusePort = reusePort;
        return this;
    }


    // int SO_RCVBUF (before bind)
    private Integer receiveBufferSize;

    public Integer getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public SocketOptionsHolder setReceiveBufferSize(Integer receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }


    // int SO_SNDBUF (before bind)
    private Integer sendBufferSize;

    public Integer getSendBufferSize() {
        return sendBufferSize;
    }

    public SocketOptionsHolder setSendBufferSize(Integer sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return this;
    }


    // int SO_LINGER
    private Integer linger;

    public Integer getLinger() {
        return linger;
    }

    public SocketOptionsHolder setLinger(Integer linger) {
        this.linger = linger;
        return this;
    }


    public void applyPreConnect(SocketChannel channel) {
        if(channel == null)
            return;
        /* TCP_NODELAY  */ trySetSocketApi(channel, s -> { if(tcpNoDelay != null) s.setTcpNoDelay(tcpNoDelay); });
        /* IP_TOS       */ trySetSocketApi(channel, s -> { if(trafficClass != null) s.setTrafficClass(trafficClass); });
        /* SO_OOBINLINE */ trySetSocketApi(channel, s -> { if(oobInline != null) s.setOOBInline(oobInline); });
        /* SO_KEEPALIVE */ trySetSocketApi(channel, s -> { if(keepAlive != null) s.setKeepAlive(keepAlive); });
        /* SO_RCVBUF    */ trySetOption(channel, StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        /* SO_SNDBUF    */ trySetOption(channel, StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        /* SO_LINGER    */ trySetSocketApi(channel, s -> { if(linger != null) s.setSoLinger(linger >= 0, linger); });
    }

    public void applyPostConnect(SocketChannel channel) {
        if(channel == null)
            return;
        /* TCP_QUICKACK     */ trySetOption(channel, ExtendedSocketOptions.TCP_QUICKACK, tcpQuickAck);
        /* TCP_KEEPIDLE     */ trySetOption(channel, ExtendedSocketOptions.TCP_KEEPIDLE, tcpKeepIdle);
        /* TCP_KEEPINTERVAL */ trySetOption(channel, ExtendedSocketOptions.TCP_KEEPINTERVAL, tcpKeepInterval);
        /* TCP_KEEPCOUNT    */ trySetOption(channel, ExtendedSocketOptions.TCP_KEEPCOUNT, tcpKeepCount);
        // in case:
        /* SO_KEEPALIVE */ trySetSocketApi(channel, s -> { if(keepAlive != null) s.setKeepAlive(keepAlive); });
        /* TCP_NODELAY  */ trySetSocketApi(channel, s -> { if(tcpNoDelay != null) s.setTcpNoDelay(tcpNoDelay); });
    }

    public void applyServerPreBind(ServerSocketChannel channel) {
        if(channel == null)
            return;
        /* SO_REUSEADDR */ trySetOption(channel, StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        /* SO_REUSEPORT */ trySetOption(channel, StandardSocketOptions.SO_REUSEPORT, reusePort);
        /* SO_RCVBUF    */ trySetOption(channel, StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
    }


    @Override
    public String toString() {
        return SocketOptionsHolder.class.getSimpleName() + "{" + this.optionsToString() + "}";
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


    private static <T> void trySetOption(SocketChannel channel, SocketOption<T> option, T value) {
        if(value == null)
            return;
        try {
            channel.setOption(option, value);
        }catch(UnsupportedOperationException | IllegalArgumentException | IOException ignored) { }
    }

    private static <T> void trySetOption(ServerSocketChannel channel, SocketOption<T> option, T value) {
        if(value == null)
            return;
        try {
            channel.setOption(option, value);
        }catch(UnsupportedOperationException | IllegalArgumentException | IOException ignored) { }
    }

    private static void trySetSocketApi(SocketChannel channel, SocketConsumer setter) {
        try {
            setter.accept(channel.socket());
        }catch(Exception ignored) { }
    }

}
