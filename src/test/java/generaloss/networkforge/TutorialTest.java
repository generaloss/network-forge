package generaloss.networkforge;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;

public class TutorialTest {

    public static void main(String[] args) throws Exception {
        // create server
        TCPServer server = new TCPServer();
        server.setOnConnect(connection ->
            connection.send("Hello, client!")
        );
        server.setOnReceive((senderConnection, byteArray) -> {
            String received = new String(byteArray);
            System.out.println(received); // Output: Hello, server!
        });
        server.setOnDisconnect((connection, reason, e) -> {
            server.close(); // close server
        });
        server.run(5555);

        // create client
        TCPClient client = new TCPClient();
        client.setOnReceive((connection, byteArray) -> {
            String received = new String(byteArray);
            System.out.println(received); // Output: Hello, client!
            client.close(); // disconnect client
        });
        client.connect("localhost", 5555);
        client.send("Hello, server!");

        // wait for server & exit
        TimeUtils.waitFor(server::isClosed);
    }

}
