package generaloss.networkforge;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.packet.PacketDispatcher;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TcpTests {

    @Test
    public void reconnect_client() throws Exception {
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> counter.incrementAndGet())
            .setOnDisconnect((connection, reason, e) -> counter.incrementAndGet())
            .run(65000);

        final TCPClient client = new TCPClient();
        for(int i = 0; i < reconnectsNum; i++){
            client.connect("localhost", 65000);
            client.close();
        }

        TimeUtils.waitFor(() -> counter.get() == reconnectsNum * 2, 500, () -> Assert.fail(counter.get() + " / " + (reconnectsNum * 2)));
        server.close();
    }

    @Test
    public void disconnect_client() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .setOnConnect(TCPConnection::close)
            .run(5406);

        final TCPClient client = new TCPClient()
            .setOnDisconnect((connection, reason, e) -> closed.set(true))
            .connect("localhost", 5406);

        TimeUtils.waitFor(closed::get, 5000, Assert::fail);
        server.close();
    }

    @Test
    public void close_server_connection() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .setOnDisconnect((connection, reason, e) -> closed.set(true))
            .run(5407);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5407);
        client.close();

        TimeUtils.waitFor(closed::get, 500, Assert::fail);
        server.close();
    }

    @Test
    public void send_hello_world_to_server() throws Exception {
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(5400);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5400);
        client.send(message);

        TimeUtils.waitFor(client::isClosed, 1000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_hello_world_encrypted() throws Exception {
        final SecretKey key = CryptoUtils.generateSecretKey(128);
        final Cipher encryptCipher = CryptoUtils.getEncryptCipher(key);
        final Cipher decryptCipher = CryptoUtils.getDecryptCipher(key);

        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> connection.ciphers().setCiphers(encryptCipher, decryptCipher))
            .setOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(5405);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5405);
        client.setCiphers(encryptCipher, decryptCipher);
        client.send(message);

        TimeUtils.waitFor(client::isClosed);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_a_lot_of_data_encrypted() throws Exception {
        final SecretKey key = CryptoUtils.generateSecretKey(128);
        final Cipher encryptCipher = CryptoUtils.getEncryptCipher(key);
        final Cipher decryptCipher = CryptoUtils.getDecryptCipher(key);

        final String message = "0123456789".repeat(1000);

        final AtomicInteger counter = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> connection.ciphers().setCiphers(encryptCipher, decryptCipher))
            .setOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                counter.incrementAndGet();

                if(!message.equals(received)){
                    hasNotEqual.set(true);
                    sender.close();
                }
            })
            .run(5408, 5409);

        final TCPClient client = new TCPClient()
            .connect("localhost", 5408 + (int) Math.round(Math.random()))
            .setCiphers(encryptCipher, decryptCipher);

        final int iterations = 10000;
        for(int i = 0; i < iterations; i++)
            client.send(message);

        TimeUtils.waitFor(() -> counter.get() == iterations, 5000);
        server.close();
        Assert.assertFalse(hasNotEqual.get());
    }

    @Test
    public void send_hello_world_to_client() throws Exception {
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> connection.send(message))
            .run(5401);

        final TCPClient client = new TCPClient()
            .setOnReceive((connection, bytes) -> {
                result.set(new String(bytes));
                connection.close();
            })
            .connect("localhost", 5401);

        TimeUtils.waitFor(client::isClosed);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_a_lot_of_data_to_server() throws Exception {
        final String message = "Hello, Data! ".repeat(1000000);
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder()
            .setMaxPacketSize(message.length());

        final TCPServer server = new TCPServer(options)
            .setOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(5402);

        final TCPClient client = new TCPClient(options)
            .connect("localhost", 5402);

        client.send(message);

        TimeUtils.waitFor(client::isClosed, 5000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void ignore_too_large_packets() throws Exception {
        final String message = "Hello, Message! ";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .setOnConnect(connection ->
                connection.options()
                    .setCloseOnPacketLimit(false)
                    .setMaxPacketSize(message.length())
            )
            .run(5402);

        final TCPClient client = new TCPClient()
            .connect("localhost", 5402);

        client.send(message.repeat(2)); // reach bytes limit => will be ignored
        client.send(message);

        TimeUtils.waitFor(client::isClosed, 1000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void connect_a_lot_of_clients_and_send_a_lot_of_data_multithreaded() throws Exception {
        final String message = "Hello, World! ".repeat(10000);
        final int clientsAmount = 100;
        final AtomicInteger done = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                if(!received.equals(message))
                    hasNotEqual.set(true);
                done.incrementAndGet();
            })
            .run(5404);

        final ConcurrentLinkedQueue<TCPClient> clients = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < clientsAmount; i++){
            final TCPClient client = new TCPClient();
            client.connect("localhost", 5404);
            clients.add(client);
        }

        for(TCPClient client : clients)
            new Thread(() -> {
                client.send(message.getBytes());
                client.close();
            }).start();

        int prevDone = -1;
        while(done.get() != clientsAmount){
            if(done.get() != prevDone)
                prevDone = done.get();
            Thread.onSpinWait();
        }
        server.close();

        Assert.assertFalse(hasNotEqual.get());
    }


    @Test
    public void send_packet() throws Exception {
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final Executor executor = Executors.newFixedThreadPool(1);

        final PacketDispatcher dispatcher = new PacketDispatcher()
            .setHandleExecutor(executor)
            .register(TestMessagePacket.class);

        final AtomicInteger counter = new AtomicInteger();
        final TestPacketHandler handler = (received) -> {
            result.set(received);
            counter.incrementAndGet();
        };

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> dispatcher.dispatch(bytes, handler))
            .run(5403);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5403);

        client.send(new TestMessagePacket(message));
        client.send(new TestMessagePacket(message));

        TimeUtils.waitFor(() -> (counter.get() == 2), 2000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

}
