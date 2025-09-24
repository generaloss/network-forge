package generaloss.networkforge.tcp;

import java.net.Socket;
import java.net.SocketException;

public class TCPConnectionOptions {

    private static final int DEFAULT_MAX_PACKET_SIZE = (8 * 1024 * 1024); // 8 Mb.  (Integer.MAX_VALUE ≈ 2 Gb)

    private final Socket socket;

    public TCPConnectionOptions(Socket socket) {
        if(socket == null)
            throw new NullPointerException("socket is null");
        this.socket = socket;
    }

    public Socket socket() {
        return socket;
    }


    // Maximum Read Packet Size
    private int maxReadPacketSize = DEFAULT_MAX_PACKET_SIZE;

    public int getMaxReadPacketSize() {
        return maxReadPacketSize;
    }

    public TCPConnectionOptions setMaxReadPacketSize(int maxSize) {
        maxReadPacketSize = maxSize;
        return this;
    }


    // Maximum Write Packet Size
    private int maxWritePacketSize = DEFAULT_MAX_PACKET_SIZE;

    public int getMaxWritePacketSize() {
        return maxWritePacketSize;
    }

    public TCPConnectionOptions setMaxWritePacketSize(int maxSize) {
        maxWritePacketSize = maxSize;
        return this;
    }

    public TCPConnectionOptions setMaxPacketSize(int maxSize) {
        maxReadPacketSize = maxSize;
        maxWritePacketSize = maxSize;
        return this;
    }


    // Close If Packet Limit Reached
    private boolean closeOnPacketLimit = true;

    public boolean isCloseOnPacketLimit() {
        return closeOnPacketLimit;
    }

    public TCPConnectionOptions setCloseOnPacketLimit(boolean closeOnPacketLimit) {
        this.closeOnPacketLimit = closeOnPacketLimit;
        return this;
    }


    public boolean getTcpNoDelay() {
        try{
            return socket.getTcpNoDelay();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setTcpNoDelay(boolean on) {
        try{
            socket.setTcpNoDelay(on);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public int getSoLinger() {
        try{
            return socket.getSoLinger();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setSoLinger(boolean on, int linger) {
        try{
            socket.setSoLinger(on, linger);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public boolean getKeepAlive() {
        try{
            return socket.getKeepAlive();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setKeepAlive(boolean on) {
        try{
            socket.setKeepAlive(on);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public int getSendBufferSize() {
        try{
            return socket.getSendBufferSize();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setSendBufferSize(int size) {
        try{
            socket.setSendBufferSize(size);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public int getReceiveBufferSize() {
        try{
            return socket.getReceiveBufferSize();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setReceiveBufferSize(int size) {
        try{
            socket.setReceiveBufferSize(size);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public int getTrafficClass() {
        try{
            return socket.getTrafficClass();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setTrafficClass(int typeOfService) {
        try{
            socket.setTrafficClass(typeOfService);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public boolean getReuseAddress() {
        try{
            return socket.getReuseAddress();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setReuseAddress(boolean on) {
        try{
            socket.setReuseAddress(on);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    public boolean getOOBInline() {
        try{
            return socket.getOOBInline();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public TCPConnectionOptions setOOBInline(boolean on) {
        try{
            socket.setOOBInline(on);
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    @Override
    public String toString() {
        return "TCPSocketOptions{" +
            "tcpNoDelay=" + this.getTcpNoDelay() +
            ", soLinger=" + this.getSoLinger() +
            ", keepAlive=" + this.getKeepAlive() +
            ", sendBufferSize=" + this.getSendBufferSize() +
            ", receiveBufferSize=" + this.getReceiveBufferSize() +
            ", trafficClass=" + this.getTrafficClass() +
            ", reuseAddress=" + this.getReuseAddress() +
            ", oobInline=" + this.getOOBInline() +
            '}';
    }

}
