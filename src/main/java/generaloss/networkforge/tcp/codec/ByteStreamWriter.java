package generaloss.networkforge.tcp.codec;

import java.io.IOException;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface ByteStreamWriter {

    void write(ByteBuffer buffer) throws IOException;

}
