package generaloss.networkforge.tcp;

@FunctionalInterface
public interface TCPCloseable {

    void close(TCPConnection connection, String message);

}
