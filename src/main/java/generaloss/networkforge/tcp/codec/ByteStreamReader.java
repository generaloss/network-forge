package generaloss.networkforge.tcp.codec;

import java.io.IOException;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface ByteStreamReader {

    int read(ByteBuffer buffer) throws IOException;

}
