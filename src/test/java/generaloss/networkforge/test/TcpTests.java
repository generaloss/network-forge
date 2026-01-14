package generaloss.networkforge.test;

import generaloss.networkforge.test.layer.CompressionLayer;
import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.packet.*;
import generaloss.networkforge.test.layer.tls.ClientTLSLayer;
import generaloss.networkforge.test.layer.tls.ServerTLSLayer;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.listener.ErrorListener;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;
import generaloss.networkforge.test.packet.TestDisconnectPacket;
import generaloss.networkforge.test.packet.TestMessagePacket;
import generaloss.networkforge.test.packet.TestPacketHandler;
import generaloss.resourceflow.resource.Resource;
import org.junit.Assert;
import org.junit.Before;
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

    private int PORT = 5400;

    @Before
    public void setup() {
        PORT++;
    }

    @Test
    public void reconnect_client() throws Exception {
        TimeUtils.delayMillis(100);
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer()
            .registerOnConnect((connection) -> counter.incrementAndGet())
            .registerOnDisconnect((connection, reason, e) -> counter.incrementAndGet())
            .run(PORT);

        final TCPClient client = new TCPClient();
        for(int i = 0; i < reconnectsNum; i++){
            client.connect("localhost", PORT);
            client.close();
        }

        TimeUtils.waitFor(() -> counter.get() == reconnectsNum * 2, 500, () -> Assert.fail(counter.get() + " / " + (reconnectsNum * 2)));
        server.close();
    }

    @Test
    public void reconnect_client_2() throws Exception {
        TimeUtils.delayMillis(100);
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer()
            .registerOnConnect((connection) -> {
                counter.incrementAndGet();
                connection.close();
            })
            .registerOnDisconnect((connection, reason, e) -> counter.incrementAndGet())
            .run(PORT);

        final AtomicBoolean disconnected = new AtomicBoolean();

        final TCPClient client = new TCPClient();
        for(int i = 0; i < reconnectsNum; i++) {
            client.connect("localhost", PORT);
            client.registerOnDisconnect((connection, reason, e) -> disconnected.set(true));
            client.close();

            TimeUtils.waitFor(disconnected::get);
            disconnected.set(false);
        }

        TimeUtils.waitFor(() -> counter.get() == reconnectsNum * 2, 500, () -> Assert.fail(counter.get() + " / " + (reconnectsNum * 2)));
        server.close();
    }

    @Test
    public void disconnect_client() throws Exception {
        TimeUtils.delayMillis(100);
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .registerOnConnect(TCPConnection::close)
            .run(PORT);

        final TCPClient client = new TCPClient()
            .registerOnDisconnect((connection, reason, e) -> closed.set(true))
            .connect("localhost", PORT);

        TimeUtils.waitFor(closed::get, 5000, Assert::fail);
        server.close();
    }

    @Test
    public void close_server_connection() throws Exception {
        TimeUtils.delayMillis(100);
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .registerOnDisconnect((connection, reason, e) -> closed.set(true))
            .run(PORT);

        final TCPClient client = new TCPClient();
        client.connect("localhost", PORT);
        client.close();

        TimeUtils.waitFor(closed::get, 500, Assert::fail);
        server.close();
    }

    @Test
    public void send_data_compressed() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "0123456789".repeat(1000);

        final AtomicInteger counter = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.getEventPipeline().addHandlerFirst(new CompressionLayer());
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            counter.incrementAndGet();

            if(!message.equals(received)){
                hasNotEqual.set(true);
                sender.close();
            }
        });
        server.run(PORT);

        final TCPClient client = new TCPClient();
        client.getEventPipeline().addHandlerFirst(new CompressionLayer());
        client.connect("localhost", PORT);

        final int iterations = 10000;
        for(int i = 0; i < iterations; i++)
            client.send(message);

        TimeUtils.waitFor(() -> counter.get() == iterations, 5000);
        server.close();
        Assert.assertFalse(hasNotEqual.get());
    }

    @Test
    public void send_hello_world_to_client() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .registerOnConnect((connection) -> connection.send(message))
            .run(PORT);

        final TCPClient client = new TCPClient()
            .registerOnReceive((connection, bytes) -> {
                result.set(new String(bytes));
                connection.close();
            })
            .connect("localhost", PORT);

        TimeUtils.waitFor(client::isClosed);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_a_lot_of_data_to_server() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, Data! ".repeat(1000000);
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder()
                                                       .setMaxFrameSize(message.length());

        final TCPServer server = new TCPServer()
            .setInitialOptions(options)
            .registerOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(PORT);

        final TCPClient client = new TCPClient()
            .setInitialOptions(options)
            .connect("localhost", PORT);

        client.send(message);

        TimeUtils.waitFor(client::isClosed, 5000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void ignore_too_large_packets() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, Message! ";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer();
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.registerOnConnect(connection ->
            connection.getOptions()
                .setCloseOnFrameReadSizeExceed(false)
                .setMaxFrameSize(message.length())
         );
        server.run(PORT);

        final TCPClient client = new TCPClient();
        client.connect("localhost", PORT);

        client.send(message.repeat(2)); // reach bytes limit => will be ignored
        client.send(message);

        TimeUtils.waitFor(client::isClosed, 1000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void connect_a_lot_of_clients_and_send_a_lot_of_data_multithreaded() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, World! ".repeat(10000);
        final int clientsAmount = 100;
        final AtomicInteger done = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            if(!received.equals(message))
                hasNotEqual.set(true);
            done.incrementAndGet();
        });
        server.run(PORT);

        final ConcurrentLinkedQueue<TCPClient> clients = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < clientsAmount; i++){
            final TCPClient client = new TCPClient();
            client.connect("localhost", PORT);
            clients.add(client);
        }

        for(TCPClient client : clients) {
            new Thread(() -> {
                client.send(message.getBytes());
                client.close();
            }).start();
        }

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
        TimeUtils.delayMillis(100);
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final PacketReader packetReader = new PacketReader()
            .register(TestMessagePacket.class, TestMessagePacket::new);

        final AtomicInteger counter = new AtomicInteger();

        final TestPacketHandler handler = new TestPacketHandler() {
            public void handleMessage(String message) {
                result.set(message);
                counter.incrementAndGet();
            }
            public void handleDisconnect(String reason) { }
        };

        final Executor executor = Executors.newSingleThreadExecutor();

        final TCPServer server = new TCPServer()
            .registerOnReceive((sender, bytes) ->
                packetReader.tryRead(bytes)
                    .ifPresent(packet ->
                        executor.execute(packet.createHandleTask(handler)))
            )
            .run(PORT);

        final TCPClient client = new TCPClient();
        client.connect("localhost", PORT);

        client.send(new TestMessagePacket(message));
        client.send(new TestMessagePacket(message));

        TimeUtils.waitFor(() -> (counter.get() == 2), 2000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_multiple_packets() throws Exception {
        TimeUtils.delayMillis(100);
        final PacketReader packetReader = new PacketReader()
            .registerAllFromPackage(Resource.classpath("generaloss/networkforge/packet/"));

        final AtomicInteger counter = new AtomicInteger();

        final TestPacketHandler handler = new TestPacketHandler() {
            public void handleMessage(String message) {
                counter.incrementAndGet();
            }
            public void handleDisconnect(String reason) {
                counter.incrementAndGet();
            }
        };

        final TCPServer server = new TCPServer()
            .registerOnReceive((sender, bytes) -> {
                final NetPacket<TestPacketHandler> packet = packetReader.readOrNull(bytes);
                packet.handle(handler);
            })
            .run(PORT);

        final TCPClient client = new TCPClient();
        client.connect("localhost", PORT);

        client.send(new TestMessagePacket("Hello, World!"));
        client.send(new TestDisconnectPacket("Disconnection"));

        TimeUtils.waitFor(() -> (counter.get() == 2), 2000);
        server.close();
    }

    @Test
    public void send_packet_ssl() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final PacketReader packetReader = new PacketReader()
            .register(TestMessagePacket.class, TestMessagePacket::new);

        final AtomicInteger counter = new AtomicInteger();

        final TestPacketHandler handler = new TestPacketHandler() {
            public void handleMessage(String message) {
                result.set(message);
                counter.incrementAndGet();
            }
            public void handleDisconnect(String reason) { }
        };

        final TCPServer server = new TCPServer();
        server.getEventPipeline().addHandlerFirst(new ServerTLSLayer());
        server.registerOnReceive((sender, bytes) -> {
            final NetPacket<TestPacketHandler> packet = packetReader.readOrNull(bytes);
            packet.handle(handler);
        });
        server.registerOnError(ErrorListener::printErrorCatch);
        server.run(PORT);

        final TCPClient client = new TCPClient();
        client.getEventPipeline().addHandlerFirst(new ClientTLSLayer());
        client.connect("localhost", PORT);
        client.registerOnConnect(connection -> {
            client.send(new TestMessagePacket(message));
            client.send(new TestMessagePacket(message));
        });
        client.registerOnError(ErrorListener::printErrorCatch);

        TimeUtils.waitFor(() -> (counter.get() == 2), 2000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_hello_world_to_server() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .registerOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(PORT);

        final TCPClient client = new TCPClient();
        client.connect("localhost", PORT);
        client.send(message);

        TimeUtils.waitFor(client::isClosed, 1000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_hello_world_encrypted() throws Exception {
        TimeUtils.delayMillis(100);
        final SecretKey key = CryptoUtils.generateSecretKey(128);
        final Cipher encryptCipher = CryptoUtils.getEncryptCipher(key);
        final Cipher decryptCipher = CryptoUtils.getDecryptCipher(key);

        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher))
            .registerOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(PORT);

        final TCPClient client = new TCPClient();
        client.connect("localhost", PORT);
        client.setCiphers(encryptCipher, decryptCipher);
        client.send(message);

        TimeUtils.waitFor(client::isClosed);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_a_lot_of_data_encrypted() throws Exception {
        TimeUtils.delayMillis(100);
        final SecretKey key = CryptoUtils.generateSecretKey(128);
        final Cipher encryptCipher = CryptoUtils.getEncryptCipher(key);
        final Cipher decryptCipher = CryptoUtils.getDecryptCipher(key);

        final String message = "0123456789".repeat(1000);

        final AtomicInteger counter = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer()
            .registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher))
            .registerOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                counter.incrementAndGet();

                if(!message.equals(received)){
                    hasNotEqual.set(true);
                    sender.close();
                }
            })
            .run(PORT, PORT + 1);

        final TCPClient client = new TCPClient()
            .connect("localhost", PORT + (int) Math.round(Math.random()))
            .setCiphers(encryptCipher, decryptCipher);

        final int iterations = 10000;
        for(int i = 0; i < iterations; i++)
            client.send(message);

        TimeUtils.waitFor(() -> counter.get() == iterations, 5000);
        server.close();
        Assert.assertFalse(hasNotEqual.get());
    }

}
