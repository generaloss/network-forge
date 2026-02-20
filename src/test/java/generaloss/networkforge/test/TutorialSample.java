package generaloss.networkforge.test;

import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;

import java.io.IOException;

public class TutorialSample {

    public static void main(String[] args) throws Exception {
        createServer();
        createClient();
    }

    private static void createServer() throws IOException {
        TCPServer server = new TCPServer();

        server.registerOnReceive((sender_connection, bytes) -> {
            String message = new String(bytes);
            System.out.println(message); // Output - Hello!

            server.close();
        });

        server.run(5555);
    }

    private static void createClient() throws IOException, InterruptedException {
        TCPClient client = new TCPClient();
        client.connect("localhost", 5555);

        client.send("Hello");
        client.getConnection().awaitWriteDrain(1000L);
    }

}
