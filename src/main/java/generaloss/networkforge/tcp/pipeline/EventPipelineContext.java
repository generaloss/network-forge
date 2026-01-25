package generaloss.networkforge.tcp.pipeline;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class EventPipelineContext {

    private final EventPipeline eventPipeline;
    private final TCPConnection connection;
    private int pipelineStartIndex;

    public EventPipelineContext(EventPipeline eventPipeline, TCPConnection connection) {
        this.eventPipeline = eventPipeline;
        this.connection = connection;
    }

    public EventPipeline getEventPipeline() {
        return eventPipeline;
    }

    public TCPConnection getConnection() {
        return connection;
    }

    public void setPipelineStartIndex(int pipelineStartIndex) {
        this.pipelineStartIndex = pipelineStartIndex;
    }

    public int getPipelineStartIndex() {
        return pipelineStartIndex;
    }


    public boolean fireSend(TCPConnection connection, byte[] data) {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        if(connection.isClosed())
            return false;

        final byte[] processedData = eventPipeline.fireOnSend(pipelineStartIndex, connection, data);
        if(processedData == null)
            return false;

        return connection.sendDirect(processedData);
    }

    public boolean fireSend(byte[] data) {
        return this.fireSend(connection, data);
    }

    public boolean fireSend(TCPConnection connection, ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);
        return this.fireSend(connection, byteArray);
    }

    public boolean fireSend(ByteBuffer buffer) {
        return this.fireSend(connection, buffer);
    }

    public boolean fireSend(TCPConnection connection, String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.fireSend(connection, string.getBytes());
    }

    public boolean fireSend(String string) {
        return this.fireSend(connection, string);
    }

    public boolean fireSend(TCPConnection connection, BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.fireSend(connection, byteArray);

        } catch (IOException e) {
            eventPipeline.fireOnError(pipelineStartIndex, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(BinaryStreamWriter streamWriter) {
        return this.fireSend(connection, streamWriter);
    }

    public boolean fireSend(TCPConnection connection, NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.fireSend(connection, byteArray);

        } catch (IOException e) {
            eventPipeline.fireOnError(pipelineStartIndex, connection, ErrorSource.SEND, e);
            return false;
        }
    }

    public boolean fireSend(NetPacket<?> packet) {
        return this.fireSend(connection, packet);
    }


    public void fireOnConnect(TCPConnection connection) {
        eventPipeline.fireOnConnect(pipelineStartIndex, connection);
    }

    public void fireOnConnect() {
        this.fireOnConnect(connection);
    }


    public void fireOnDisconnect(TCPConnection connection, CloseReason reason, Exception e) {
        eventPipeline.fireOnDisconnect(pipelineStartIndex, connection, reason, e);
    }

    public void fireOnDisconnect(CloseReason reason, Exception e) {
        this.fireOnDisconnect(connection, reason, e);
    }


    public void fireOnReceive(TCPConnection connection, byte[] data) {
        eventPipeline.fireOnReceive(pipelineStartIndex, connection, data);
    }

    public void fireOnReceive(byte[] data) {
        this.fireOnReceive(connection, data);
    }


    public void fireOnError(TCPConnection connection, ErrorSource source, Throwable throwable) {
        eventPipeline.fireOnError(pipelineStartIndex, connection, source, throwable);
    }

    public void fireOnError(ErrorSource source, Throwable throwable) {
        this.fireOnError(connection, source, throwable);
    }

}
