package generaloss.networkforge.tcp.io;

@FunctionalInterface
public interface IOHandlerFactory {

    ConnectionIOHandler create();

}
