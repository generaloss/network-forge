package generaloss.networkforge;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

@FunctionalInterface
public interface SocketConsumer {

    void accept(Socket socket) throws IOException;

    default SocketConsumer andThen(SocketConsumer after) {
        Objects.requireNonNull(after);
        return (t) -> {
            this.accept(t);
            after.accept(t);
        };
    }

}