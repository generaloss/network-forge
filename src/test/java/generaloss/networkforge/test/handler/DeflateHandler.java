package generaloss.networkforge.test.handler;

import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.networkforge.tcp.pipeline.EventHandler;
import generaloss.networkforge.tcp.pipeline.EventInvocationContext;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class DeflateHandler extends EventHandler {

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int MINIMUM_SIZE_TO_COMPRESS = 100;
    
    private final int compressionLevel;
    private final byte[] deflaterBuffer;
    private final byte[] inflaterBuffer;

    public DeflateHandler(int compressionLevel, int bufferSize) {
        this.compressionLevel = compressionLevel;
        this.deflaterBuffer = new byte[bufferSize];
        this.inflaterBuffer = new byte[bufferSize];
    }

    public DeflateHandler() {
        this(Deflater.DEFAULT_COMPRESSION, DEFAULT_BUFFER_SIZE);
    }

    @Override
    public byte[] handleSend(EventInvocationContext context, byte[] data) {
        if(data.length < MINIMUM_SIZE_TO_COMPRESS)
            return data;

        try {
            final Deflater deflater = new Deflater(compressionLevel);
            deflater.setInput(data);
            deflater.finish();
            
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            
            while(!deflater.finished()) {
                final int length = deflater.deflate(deflaterBuffer);
                outputStream.write(deflaterBuffer, 0, length);
            }
            
            deflater.end();
            final byte[] compressed = outputStream.toByteArray();

            final byte[] result = new byte[compressed.length + 1];
            result[0] = 1;
            System.arraycopy(compressed, 0, result, 1, compressed.length);

            return result;
        } catch (Exception e) {
            context.fireError(ErrorSource.SEND_HANDLER, e);
            return data;
        }
    }
    
    @Override
    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        if(data[0] != 1)
            return true;

        try {
            final byte[] compressed = new byte[data.length - 1];
            System.arraycopy(data, 1, compressed, 0, compressed.length);

            final Inflater inflater = new Inflater();
            inflater.setInput(compressed);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressed.length * 2);
            
            while(!inflater.finished()) {
                final int length = inflater.inflate(inflaterBuffer);
                outputStream.write(inflaterBuffer, 0, length);
            }
            
            inflater.end();
            final byte[] decompressed = outputStream.toByteArray();

            context.fireReceive(decompressed);
            return false;
        } catch (Exception e) {
            context.fireError(ErrorSource.RECEIVE_HANDLER, e);
            return false;
        }
    }

}