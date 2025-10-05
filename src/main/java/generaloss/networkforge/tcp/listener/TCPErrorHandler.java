package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

public interface TCPErrorHandler {

    void error(TCPConnection connection, TCPErrorSource source, Throwable throwable);

    static void printErrorCatch(Class<?> c, TCPConnection connection, TCPErrorSource source, Throwable throwable) {
        System.err.println(
            "[" + c.getSimpleName() + "-ErrorHandler] Error in " + source + ".\n" +
            "TCPConnection name: '" + connection.getName() + "'.\n" +
            "Caught and ignored to prevent server crash:"
        );
        throwable.printStackTrace(System.err);
        System.err.println();
    }

}
