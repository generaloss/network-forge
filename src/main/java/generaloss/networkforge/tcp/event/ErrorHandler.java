package generaloss.networkforge.tcp.event;

import generaloss.networkforge.tcp.TCPConnection;

public interface ErrorHandler {

    void onError(TCPConnection connection, ErrorSource source, Throwable throwable);

    static void printErrorCatch(String sourceName, TCPConnection connection, ErrorSource source, Throwable throwable) {
        System.err.println(
            "[" + sourceName + "-ErrorHandler] Error in " + source + ".\n" +
            "TCPConnection name: '" + connection.getName() + "'.\n" +
            "Caught and ignored to prevent server crash:"
        );
        throwable.printStackTrace(System.err);
        System.err.println();
    }

}
