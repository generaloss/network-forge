package generaloss.networkforge.tcp.iohandler;

public enum IOHandlerType {

    FRAMED (FramedConnectionIOHandler::new),
    STREAM (StreamConnectionIOHandler::new);

    private final IOHandlerFactory factory;

    IOHandlerType(IOHandlerFactory factory) {
        this.factory = factory;
    }

    public IOHandlerFactory getFactory() {
        return factory;
    }

    public static final IOHandlerType DEFAULT = FRAMED;

}
