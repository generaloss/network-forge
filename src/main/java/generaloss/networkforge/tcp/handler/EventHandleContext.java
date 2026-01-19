package generaloss.networkforge.tcp.handler;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.SendContext;
import generaloss.networkforge.tcp.Sendable;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.nio.ByteBuffer;

public class EventHandleContext implements Sendable {

    private final EventPipeline eventPipeline;
    private final TCPConnection connection;
    private final SendContext sendContext;

    private int nextPipelineIndex;

    public EventHandleContext(EventPipeline eventPipeline, TCPConnection connection) {
        this.eventPipeline = eventPipeline;
        this.connection = connection;
        this.sendContext = new SendContext(connection, eventPipeline);
    }

    public EventPipeline getEventPipeline() {
        return eventPipeline;
    }

    public TCPConnection getConnection() {
        return connection;
    }

    public void onPipelineIndexChanged(int index) {
        nextPipelineIndex = (index + 1);
        sendContext.setPipelineStartIndex(nextPipelineIndex);
    }


    @Override
    public boolean send(byte[] data) {
        return sendContext.send(data);
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        return sendContext.send(buffer);
    }

    @Override
    public boolean send(String string) {
        return sendContext.send(string);
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        return sendContext.send(streamWriter);
    }

    @Override
    public boolean send(NetPacket<?> packet) {
        return sendContext.send(packet);
    }


    public void fireOnConnectNext(TCPConnection connection) {
        eventPipeline.fireOnConnect(nextPipelineIndex, connection);
    }

    public void fireOnConnectNext() {
        this.fireOnConnectNext(connection);
    }


    public void fireOnDisconnectNext(TCPConnection connection, CloseReason reason, Exception e) {
        eventPipeline.fireOnDisconnect(nextPipelineIndex, connection, reason, e);
    }

    public void fireOnDisconnectNext(CloseReason reason, Exception e) {
        this.fireOnDisconnectNext(connection, reason, e);
    }


    public void fireOnReceiveNext(TCPConnection connection, byte[] data) {
        eventPipeline.fireOnReceive(nextPipelineIndex, connection, data);
    }

    public void fireOnReceiveNext(byte[] data) {
        this.fireOnReceiveNext(connection, data);
    }


    public void fireOnErrorNext(TCPConnection connection, ErrorSource source, Throwable throwable) {
        eventPipeline.fireOnError(nextPipelineIndex, connection, source, throwable);
    }

    public void fireOnErrorNext(ErrorSource source, Throwable throwable) {
        this.fireOnErrorNext(connection, source, throwable);
    }

}
