package generaloss.networkforge.tcp.iohandler;

@FunctionalInterface
public interface IOHandlerFactory {

    ConnectionIOHandler create();

}
