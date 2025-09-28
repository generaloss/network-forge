package generaloss.networkforge;

import java.io.IOException;
import java.net.Socket;

@FunctionalInterface
public interface SocketFunction<R> {

    R apply(Socket socket) throws IOException;

}
