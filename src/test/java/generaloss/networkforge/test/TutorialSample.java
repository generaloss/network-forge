package generaloss.networkforge.test;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;

public class TutorialSample {

    public static void main(String[] args) throws Exception {
        // create server
        TCPServer server = new TCPServer();
        server.registerOnConnect(connection ->
            connection.send("Hello, client!")
        );
        server.registerOnReceive((senderConnection, data) -> {
            String received = new String(data);
            System.out.println(received); // Output: Hello, server!
        });
        server.registerOnDisconnect((connection, reason, e) -> {
            server.close(); // close server
        });
        server.run(5555);

        // create client
        TCPClient client = new TCPClient();
        client.registerOnReceive((connection, data) -> {
            String received = new String(data);
            System.out.println(received); // Output: Hello, client!
            client.close(); // disconnect client
        });
        client.connect("localhost", 5555);
        client.send("Hello, server!");

        // wait for server & exit
        TimeUtils.waitFor(server::isClosed);
    }

}
