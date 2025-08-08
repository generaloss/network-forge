package generaloss.networkforge.tcp;

public interface TCPErrorHandler {

    void error(TCPConnection connection, String source, Exception exception);

    static void printErrorCatch(TCPConnection connection, String source, Exception exception) {
        System.err.println(
            "[TCPServer ErrorHandler] Exception in " + source + ".\n" +
            "TCPConnection name: '" + connection.getName() + "'.\n" +
            "Caught and ignored to prevent server crash:\n"
        );
        exception.printStackTrace(System.err);
    }

}
