package generaloss.networkforge.tcp;

import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.tcp.handler.EventPipeline;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.resourceflow.stream.BinaryStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SendContext implements Sendable {

    private final TCPConnection connection;
    private final EventPipeline eventPipeline;
    private int pipelineStartIndex;

    public SendContext(TCPConnection connection, EventPipeline eventPipeline) {
        this.connection = connection;
        this.eventPipeline = eventPipeline;
    }

    public void setPipelineStartIndex(int pipelineStartIndex) {
        this.pipelineStartIndex = pipelineStartIndex;
    }

    public int getPipelineStartIndex() {
        return pipelineStartIndex;
    }


    @Override
    public boolean send(byte[] data) {
        if(data == null)
            throw new IllegalArgumentException("Argument 'data' cannot be null");

        if(connection.isClosed())
            return false;

        final byte[] processedData = eventPipeline.fireOnSend(pipelineStartIndex, connection, data);
        if(processedData == null)
            return false;

        return connection.sendDirect(processedData);
    }

    @Override
    public boolean send(ByteBuffer buffer) {
        if(buffer == null)
            throw new IllegalArgumentException("Argument 'buffer' cannot be null");

        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.duplicate().get(byteArray);
        return this.send(byteArray);
    }

    @Override
    public boolean send(String string) {
        if(string == null)
            throw new IllegalArgumentException("Argument 'string' cannot be null");

        return this.send(string.getBytes());
    }

    @Override
    public boolean send(BinaryStreamWriter streamWriter) {
        if(streamWriter == null)
            throw new IllegalArgumentException("Argument 'streamWriter' cannot be null");

        try {
            final byte[] byteArray = BinaryStreamWriter.toByteArray(streamWriter);
            return this.send(byteArray);

        } catch (IOException e) {
            eventPipeline.fireOnError(connection, ErrorSource.SEND, e);
            return false;
        }
    }

    @Override
    public boolean send(NetPacket<?> packet) {
        if(packet == null)
            throw new IllegalArgumentException("Argument 'packet' cannot be null");

        try {
            final byte[] byteArray = packet.toByteArray();
            return this.send(byteArray);

        } catch (IOException e) {
            eventPipeline.fireOnError(connection, ErrorSource.SEND, e);
            return false;
        }
    }

}
