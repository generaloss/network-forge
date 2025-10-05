package generaloss.networkforge.tcp.options;

import generaloss.networkforge.SocketConsumer;
import generaloss.networkforge.SocketFunction;
import jdk.net.ExtendedSocketOptions;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketOption;
import java.net.StandardSocketOptions;

/* TCP Socket Options:
boolean  TCP_NODELAY
boolean  TCP_QUICKACK
int      TCP_KEEPIDLE
int      TCP_KEEPINTERVAL
int      TCP_KEEPCOUNT
int      IP_TOS
boolean  SO_OOBINLINE
boolean  SO_KEEPALIVE
boolean  SO_REUSEADDR
boolean  SO_REUSEPORT
int      SO_RCVBUF
int      SO_SNDBUF
int      SO_LINGER
*/
public class SocketOptions {

    private final Socket socket;

    public SocketOptions(Socket socket) {
        if(socket == null)
            throw new IllegalArgumentException("Argument 'socket' cannot be null");
        this.socket = socket;
    }

    public Socket getSocket() {
        return socket;
    }


    private void trySetSocketApi(SocketConsumer setter) {
        try {
            setter.accept(socket);
        }catch (IOException ignored) { }
    }

    private <T> T tryGetSocketApi(SocketFunction<T> setter) {
        try {
            return setter.apply(socket);
        }catch (IOException ignored) {
            return null;
        }
    }

    private <T> void trySetOption(SocketOption<T> option, T value) {
        if(value == null)
            return;
        try {
            socket.setOption(option, value);
        }catch (UnsupportedOperationException | IllegalArgumentException | IOException ignored) { }
    }

    private <T> T tryGetOption(SocketOption<T> option) {
        try {
            return socket.getOption(option);
        }catch (UnsupportedOperationException | IllegalArgumentException | IOException ignored) {
            return null;
        }
    }


    // boolean TCP_NODELAY
    public Boolean isTcpNoDelay() {
        return this.tryGetSocketApi(Socket::getTcpNoDelay);
    }

    public SocketOptions setTcpNoDelay(Boolean tcpNoDelay) {
        this.trySetSocketApi(s -> s.setTcpNoDelay(tcpNoDelay));
        return this;
    }


    // boolean TCP_QUICKACK
    public Boolean isTcpQuickAck() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_QUICKACK);
    }

    public SocketOptions setTcpQuickAck(Boolean tcpQuickAck) {
        this.trySetOption(ExtendedSocketOptions.TCP_QUICKACK, tcpQuickAck);
        return this;
    }


    // int TCP_KEEPIDLE
    public Integer getTcpKeepIdle() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_KEEPIDLE);
    }

    public SocketOptions setTcpKeepIdle(Integer tcpKeepIdle) {
        this.trySetOption(ExtendedSocketOptions.TCP_KEEPIDLE, tcpKeepIdle);
        return this;
    }


    // int TCP_KEEPINTERVAL
    public Integer getTcpKeepInterval() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_KEEPINTERVAL);
    }

    public SocketOptions setTcpKeepInterval(Integer tcpKeepInterval) {
        this.trySetOption(ExtendedSocketOptions.TCP_KEEPINTERVAL, tcpKeepInterval);
        return this;
    }


    // int TCP_KEEPCOUNT
    public Integer getTcpKeepCount() {
        return this.tryGetOption(ExtendedSocketOptions.TCP_KEEPCOUNT);
    }

    public SocketOptions setTcpKeepCount(Integer tcpKeepCount) {
        this.trySetOption(ExtendedSocketOptions.TCP_KEEPCOUNT, tcpKeepCount);
        return this;
    }


    // int IP_TOS
    public Integer getTrafficClass() {
        return this.tryGetSocketApi(Socket::getTrafficClass);
    }

    public SocketOptions setTrafficClass(Integer trafficClass) {
        this.trySetSocketApi(s -> s.setTrafficClass(trafficClass));
        return this;
    }


    // boolean SO_OOBINLINE
    public Boolean isOOBInline() {
        return this.tryGetSocketApi(Socket::getOOBInline);
    }

    public SocketOptions setOOBInline(Boolean oobInline) {
        this.trySetSocketApi(s -> s.setOOBInline(oobInline));
        return this;
    }


    // boolean SO_KEEPALIVE
    public Boolean isKeepAlive() {
        return this.tryGetSocketApi(Socket::getKeepAlive);
    }

    public SocketOptions setKeepAlive(Boolean keepAlive) {
        this.trySetSocketApi(s -> s.setKeepAlive(keepAlive));
        return this;
    }


    // boolean SO_REUSEADDR
    public Boolean isReuseAddress() {
        return this.tryGetSocketApi(Socket::getReuseAddress);
    }

    public SocketOptions setReuseAddress(Boolean reuseAddress) {
        this.trySetSocketApi(s -> s.setReuseAddress(reuseAddress));
        return this;
    }


    // boolean SO_REUSEPORT
    public Boolean isReusePort() {
        return this.tryGetOption(StandardSocketOptions.SO_REUSEPORT);
    }

    public SocketOptions setReusePort(Boolean reusePort) {
        this.trySetOption(StandardSocketOptions.SO_REUSEPORT, reusePort);
        return this;
    }


    // int SO_RCVBUF
    public Integer getReceiveBufferSize() {
        return this.tryGetSocketApi(Socket::getReceiveBufferSize);
    }

    public SocketOptions setReceiveBufferSize(Integer receiveBufferSize) {
        this.trySetSocketApi(s -> s.setReceiveBufferSize(receiveBufferSize));
        return this;
    }


    // int SO_SNDBUF
    public Integer getSendBufferSize() {
        return this.tryGetSocketApi(Socket::getSendBufferSize);
    }

    public SocketOptions setSendBufferSize(Integer sendBufferSize) {
        this.trySetSocketApi(s -> s.setSendBufferSize(sendBufferSize));
        return this;
    }


    // int SO_LINGER
    public Integer getLinger() {
        return this.tryGetSocketApi(Socket::getSoLinger);
    }

    public SocketOptions setLinger(boolean on, int linger) {
        this.trySetSocketApi(s -> s.setSoLinger(on, linger));
        return this;
    }


    @Override
    public String toString() {
        return SocketOptions.class.getSimpleName() + "{" + this.optionsToString() + "}";
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
