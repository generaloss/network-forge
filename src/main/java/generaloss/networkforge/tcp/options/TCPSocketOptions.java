package generaloss.networkforge.tcp.options;

import jdk.net.ExtendedSocketOptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;

public class TCPSocketOptions {

    private final Socket socket;

    public TCPSocketOptions(Socket socket) {
        if(socket == null)
            throw new IllegalArgumentException("socket is null");
        this.socket = socket;
    }

    public Socket getSocket() {
        return socket;
    }

    public <T> boolean trySetOption(SocketOption<T> option, T value) {
        try {
            socket.setOption(option, value);
            return true;
        }catch(IOException e) {
            throw new UncheckedIOException(e);
        }catch(UnsupportedOperationException e) {
            return false;
        }
    }

    public <T> T tryGetOption(SocketOption<T> option) {
        try {
            return socket.getOption(option);
        }catch(IOException e) {
            throw new UncheckedIOException(e);
        }catch(UnsupportedOperationException e) {
            return null;
        }
    }


    // boolean TCP_NODELAY
    public Boolean isTcpNoDelay() {
        return this.tryGetOption(StandardSocketOptions.TCP_NODELAY);
    }

    public TCPSocketOptions setTcpNoDelay(Boolean tcpNoDelay) {
        this.trySetOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        return this;
    }


    // boolean TCP_QUICKACK
    public Boolean isTcpQuickAck() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_QUICKACK);
    }

    public TCPSocketOptions setTcpQuickAck(Boolean tcpQuickAck) {
        this.trySetOption(ExtendedSocketOptions.TCP_QUICKACK, tcpQuickAck);
        return this;
    }


    // int TCP_KEEPIDLE
    public Integer getTcpKeepIdle() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_KEEPIDLE);
    }

    public TCPSocketOptions setTcpKeepIdle(Integer tcpKeepIdle) {
        this.trySetOption(ExtendedSocketOptions.TCP_KEEPIDLE, tcpKeepIdle);
        return this;
    }


    // int TCP_KEEPINTERVAL
    public Integer getTcpKeepInterval() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_KEEPINTERVAL);
    }

    public TCPSocketOptions setTcpKeepInterval(Integer tcpKeepInterval) {
        this.trySetOption(ExtendedSocketOptions.TCP_KEEPINTERVAL, tcpKeepInterval);
        return this;
    }


    // int TCP_KEEPCOUNT
    public Integer getTcpKeepCount() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_KEEPCOUNT);
    }

    public TCPSocketOptions setTcpKeepCount(Integer tcpKeepCount) {
        this.trySetOption(ExtendedSocketOptions.TCP_KEEPCOUNT, tcpKeepCount);
        return this;
    }


    // int IP_TOS
    public Integer getTrafficClass() {
        return this.tryGetOption(StandardSocketOptions.IP_TOS);
    }

    public TCPSocketOptions setTrafficClass(Integer trafficClass) {
        this.trySetOption(StandardSocketOptions.IP_TOS, trafficClass);
        return this;
    }


    // boolean SO_OOBINLINE
    public Boolean isOOBInline() {
        try{
            return socket.getOOBInline();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPSocketOptions setOOBInline(Boolean oobInline) {
        try{
            socket.setOOBInline(oobInline);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }catch(UnsupportedOperationException ignored) { }
        return this;
    }


    // boolean SO_KEEPALIVE
    public Boolean isKeepAlive() {
        return this.tryGetOption(StandardSocketOptions.SO_KEEPALIVE);
    }

    public TCPSocketOptions setKeepAlive(Boolean keepAlive) {
        this.trySetOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        return this;
    }


    // boolean SO_REUSEADDR
    public Boolean isReuseAddress() {
        return this.tryGetOption(StandardSocketOptions.SO_REUSEADDR);
    }

    public TCPSocketOptions setReuseAddress(Boolean reuseAddress) {
        this.trySetOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        return this;
    }


    // boolean SO_REUSEPORT
    public Boolean isReusePort() {
        return this.tryGetOption(StandardSocketOptions.SO_REUSEPORT);
    }

    public TCPSocketOptions setReusePort(Boolean reusePort) {
        this.trySetOption(StandardSocketOptions.SO_REUSEPORT, reusePort);
        return this;
    }


    // int SO_RCVBUF
    public Integer getReceiveBufferSize() {
        return this.tryGetOption(StandardSocketOptions.SO_RCVBUF);
    }

    public TCPSocketOptions setReceiveBufferSize(Integer receiveBufferSize) {
        this.trySetOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        return this;
    }


    // int SO_SNDBUF
    public Integer getSendBufferSize() {
        return this.tryGetOption(StandardSocketOptions.SO_SNDBUF);
    }

    public TCPSocketOptions setSendBufferSize(Integer sendBufferSize) {
        this.trySetOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        return this;
    }


    // int SO_LINGER
    public Integer getLinger() {
        return this.tryGetOption(StandardSocketOptions.SO_LINGER);
    }

    public TCPSocketOptions setLinger(Integer linger) {
        this.trySetOption(StandardSocketOptions.SO_LINGER, linger);
        return this;
    }


    @Override
    public String toString() {
        return TCPSocketOptions.class.getSimpleName() + "{" + this.optionsToString() + "}";
    }

    protected String optionsToString() {
        return "TCP_NODELAY=" + this.isTcpNoDelay() +
            ", TCP_QUICKACK=" + this.isTcpQuickAck() +
            ", TCP_KEEPIDLE=" + this.getTcpKeepIdle() +
            ", TCP_KEEPINTERVAL=" + this.getTcpKeepInterval() +
            ", TCP_KEEPCOUNT=" + this.getTcpKeepCount() +
            ", IP_TOS=" + this.getTrafficClass() +
            ", SO_OOBINLINE=" + this.isOOBInline() +
            ", SO_KEEPALIVE=" + this.isKeepAlive() +
            ", SO_REUSEADDR=" + this.isReuseAddress() +
            ", SO_REUSEPORT=" + this.isReusePort() +
            ", SO_RCVBUF=" + this.getReceiveBufferSize() +
            ", SO_SNDBUF=" + this.getSendBufferSize() +
            ", SO_LINGER=" + this.getLinger();
    }

}
