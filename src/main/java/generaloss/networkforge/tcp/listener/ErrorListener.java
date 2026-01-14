package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

public interface ErrorListener {

    void onError(TCPConnection connection, ErrorSource source, Throwable throwable);

    static void printErrorCatch(TCPConnection connection, ErrorSource source, Throwable throwable) {
        System.err.println(
            "[ErrorHandler] Error in " + source + ".\n" +
            "TCPConnection name: '" + connection.getName() + "'.\n" +
            "Caught and ignored to prevent server crash:"
        );
        throwable.printStackTrace(System.err);
        System.err.println();
    }

}
