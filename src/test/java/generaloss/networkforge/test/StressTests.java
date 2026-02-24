package generaloss.networkforge.test;

import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.pipeline.EventHandler;
import generaloss.networkforge.tcp.pipeline.EventInvocationContext;
import generaloss.networkforge.test.handler.DeflateHandler;
import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.packet.*;
import generaloss.networkforge.test.handler.tls.ClientSecureHandler;
import generaloss.networkforge.test.handler.tls.ServerSecureHandler;
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
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StressTests {

    @Test
    public void reconnect_client_1() throws Exception {
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer();
        server.registerOnConnect((connection) -> counter.incrementAndGet());
        server.registerOnDisconnect((connection, reason, e) -> counter.incrementAndGet());
        server.run(5401);

        final TCPClient client = new TCPClient();
        for(int i = 0; i < reconnectsNum; i++){
            client.connectAsync("localhost", 5401);
            TimeUtils.waitFor(client::isOpen, 3000, () -> {
                server.close();
                Assert.fail();
            });
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

        final TCPServer server = new TCPServer();
        server.registerOnConnect((connection) -> {
            counter.incrementAndGet();
            connection.close();
        });
        server.registerOnDisconnect((connection, reason, e) -> counter.incrementAndGet());
        server.run(5402);

        final AtomicBoolean disconnected = new AtomicBoolean();

        final TCPClient client = new TCPClient();
        client.registerOnDisconnect((connection, reason, e) -> disconnected.set(true));
        for(int i = 0; i < reconnectsNum; i++) {
            var future = client.connectAsync("localhost", 5402);
            TimeUtils.waitFor(future::isDone, 8000, () -> {
                server.close();
                Assert.fail();
            });
            client.close();

            TimeUtils.waitFor(disconnected::get, 8000, () -> {
                server.close();
                Assert.fail();
            });
            disconnected.set(false);
        }

        TimeUtils.waitFor(() -> counter.get() == reconnectsNum * 2, 500, () -> {
            client.close();
            server.close();
            Assert.fail(counter.get() + " / " + (reconnectsNum * 2));
        });
        server.close();
    }

    @Test
    public void disconnect_client() throws Exception {
        TimeUtils.delayMillis(100);
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnConnect(TCPConnection::close);
        server.run(5403);

        final TCPClient client = new TCPClient();
        client.registerOnDisconnect((connection, reason, e) -> closed.set(true));
        client.connect("localhost", 5403);

        TimeUtils.waitFor(closed::get, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
    }

    @Test
    public void close_server_connection() throws Exception {
        TimeUtils.delayMillis(100);
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnDisconnect((connection, reason, e) -> closed.set(true));
        server.run(5404);

        final TCPClient client = new TCPClient();
        client.connectAsync("localhost", 5404);
        TimeUtils.waitFor(client::isOpen, 3000, () -> {
            server.close();
            Assert.fail();
        });
        client.close();

        TimeUtils.waitFor(closed::get, 500, () -> {
            server.close();
            Assert.fail();
        });
        server.close();
    }

    @Test
    public void send_data_compressed() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "0123456789".repeat(1000);

        final AtomicInteger counter = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.getEventPipeline().addHandlerFirst(new DeflateHandler());
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            counter.incrementAndGet();

            if(!message.equals(received)){
                hasNotEqual.set(true);
                sender.close();
            }
        });
        server.run(5405);

        final int iterations = 10000;

        final TCPClient client = new TCPClient();
        client.getEventPipeline().addHandlerFirst(new DeflateHandler());
        client.registerOnConnect(connection -> {
            for(int i = 0; i < iterations; i++)
                client.send(message);
        });
        client.connect("localhost", 5405);

        TimeUtils.waitFor(() -> counter.get() == iterations, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertFalse(hasNotEqual.get());
    }

    @Test
    public void send_hello_world_to_client() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer();
        server.registerOnConnect((connection) -> connection.send(message));
        server.run(5406);

        final TCPClient client = new TCPClient();
        client.registerOnReceive((connection, bytes) -> {
            result.set(new String(bytes));
            connection.close();
        });
        client.connect("localhost", 5406);

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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

        final TCPServer server = new TCPServer();
        server.setInitialOptions(options);
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.run(5407);

        final TCPClient client = new TCPClient();
        client.setInitialOptions(options);
        client.connect("localhost", 5407);

        client.send(message);

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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
        server.run(5408);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5408);

        client.send(message.repeat(2)); // reach bytes limit => will be ignored
        client.send(message);

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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
        server.run(5409);

        final ConcurrentLinkedQueue<TCPClient> clients = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < clientsAmount; i++){
            final TCPClient client = new TCPClient();
            client.connect("localhost", 5409);
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

        final TCPServer server = new TCPServer();
        server.registerOnReceive((sender, bytes) ->
            packetReader.tryRead(bytes)
                .ifPresent(packet ->
                    executor.execute(packet.createHandleTask(handler)))
        );
        server.run(5410);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5410);

        client.send(new TestMessagePacket(message));
        client.send(new TestMessagePacket(message));

        TimeUtils.waitFor(() -> (counter.get() == 2), 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_multiple_packets() throws Exception {
        TimeUtils.delayMillis(100);
        final PacketReader packetReader = new PacketReader()
            .registerAllFromPackage(Resource.classpath("generaloss/networkforge/test/packet/"));

        final AtomicInteger counter = new AtomicInteger();

        final TestPacketHandler handler = new TestPacketHandler() {
            public void handleMessage(String message) {
                counter.incrementAndGet();
            }
            public void handleDisconnect(String reason) {
                counter.incrementAndGet();
            }
        };

        final TCPServer server = new TCPServer();
        server.registerOnReceive((sender, bytes) -> {
            final NetPacket<TestPacketHandler> packet = packetReader.readOrNull(bytes);
            packet.handle(handler);
        });
        server.run(5411);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5411);

        client.send(new TestMessagePacket("Hello, World!"));
        client.send(new TestDisconnectPacket("Disconnection"));

        TimeUtils.waitFor(() -> (counter.get() == 2), 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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
        server.getEventPipeline().addHandlerFirst(new ServerSecureHandler());
        server.registerOnReceive((sender, bytes) -> {
            final NetPacket<TestPacketHandler> packet = packetReader.readOrNull(bytes);
            packet.handle(handler);
        });
        server.registerOnError(ErrorListener::printErrorCatch);
        server.run(5412);

        final TCPClient client = new TCPClient();
        client.getEventPipeline().addHandlerFirst(new ClientSecureHandler());
        client.connect("localhost", 5412);
        client.registerOnConnect(connection -> {
            client.send(new TestMessagePacket(message));
            client.send(new TestMessagePacket(message));
        });
        client.registerOnError(ErrorListener::printErrorCatch);

        TimeUtils.waitFor(() -> (counter.get() == 2), 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_hello_world_to_server() throws Exception {
        TimeUtils.delayMillis(100);
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer();
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.run(5413);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5413);
        client.send(message);

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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

        final TCPServer server = new TCPServer();
        server.registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher));
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.run(5414);

        final TCPClient client = new TCPClient();
        client.registerOnConnect((connection) -> {
            connection.getCiphers().setCiphers(encryptCipher, decryptCipher);
            client.send(message);
        });
        client.connect("localhost", 5414);

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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

        final TCPServer server = new TCPServer();
        server.registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher));
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            counter.incrementAndGet();

            if(!message.equals(received)){
                hasNotEqual.set(true);
                sender.close();
            }
        });
        server.run(5415, 5416);

        final TCPClient client = new TCPClient();
        client.registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher));
        client.connect("localhost", 5415 + (int) Math.round(Math.random()));

        final int iterations = 10000;
        for(int i = 0; i < iterations; i++)
            client.send(message);

        TimeUtils.waitFor(() -> counter.get() == iterations, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertFalse(hasNotEqual.get());
    }

    @Test
    public void async_connect_timeout() throws Exception {
        TimeUtils.delayMillis(100);
        final TCPClient client = new TCPClient();

        final long timeoutMillis = 1000L;

        CompletableFuture<TCPConnection> future = client.connectAsync("google.com", 65000, timeoutMillis);

        try {
            // noinspection resource
            future.join();
            Assert.fail("Expected timeout");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof TimeoutException);
        }
    }

    @Test
    public void send_zero_length_payload_framed() throws Exception {
        TimeUtils.delayMillis(100);
        final TCPServer server = new TCPServer();
        server.setCodecFactory(CodecType.FRAMED);
        server.registerOnReceive((c, data) -> {
            if(data.length == 0)
                server.close();
        });
        server.run(5417);

        final TCPClient client = new TCPClient();
        client.setCodec(CodecType.FRAMED);
        client.connect("localhost", 5417);
        client.send(new byte[0]);

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
    }

    @Test
    public void one_client_multithreaded_send_storm() throws Exception {
        TimeUtils.delayMillis(100);

        AtomicInteger counter = new AtomicInteger();
        final byte[] testData = new byte[] { 54 };
        final int sends = 512;

        final TCPServer server = new TCPServer();
        server.setCodecFactory(CodecType.FRAMED);
        server.registerOnReceive((c, data) -> {
            if(data.length == 1 && data[0] == testData[0])
                counter.incrementAndGet();
        });
        server.run(5418);

        final TCPClient client = new TCPClient();
        client.setCodec(CodecType.FRAMED);
        client.connect("localhost", 5418);

        final ExecutorService executor = Executors.newFixedThreadPool(16);
        for(int i = 0; i < sends; i++)
            executor.execute(() -> client.send(testData));

        executor.shutdown();
        // noinspection ResultOfMethodCallIgnored
        executor.awaitTermination(3, TimeUnit.SECONDS);
        client.getConnection().awaitWriteDrain(2000);
        client.close();

        TimeUtils.waitFor(() -> sends == counter.get(), 1000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });

        server.close();
        executor.shutdownNow();
    }

    @Test
    public void multithreaded_send_storm() throws Exception {
        TimeUtils.delayMillis(100);

        AtomicInteger counter = new AtomicInteger();
        final byte[] testData = new byte[] { 54 };
        final int sends = 512;

        final TCPServer server = new TCPServer();
        server.setCodecFactory(CodecType.FRAMED);
        server.registerOnReceive((c, data) -> {
            if(data.length == 1 && data[0] == testData[0])
                counter.incrementAndGet();
        });
        server.run(5419);

        final Thread[] threads = new Thread[sends];
        for(int i = 0; i < threads.length; i++) {
            final Thread thread = new Thread(() -> {
                try {
                    final TCPClient client = new TCPClient();
                    client.connect("localhost", 5419);
                    client.send(testData);
                    client.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            });
            thread.setDaemon(true);
            threads[i] = thread;
        }

        for(Thread thread : threads)
            thread.start();

        TimeUtils.waitFor(() -> sends == counter.get(), 3000, () -> {
            server.close();
            Assert.fail(counter.get() + "/" + sends);
        });

        server.close();
    }

    @Test
    public void close_by_other_side() throws Exception {
        TimeUtils.delayMillis(100);

        final int iterations = 200;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer();
        server.registerOnDisconnect((connection, reason, e) -> {
            if(reason == CloseReason.CLOSE_BY_OTHER_SIDE) {
                final int count = counter.incrementAndGet();
                if(count == iterations)
                    server.close();
            } else {
                System.err.println(reason);
            }
        });
        server.run(5420);

        final TCPClient client = new TCPClient();
        for(int i = 0; i < iterations; i++) {
            client.connect("localhost", 5420);
            client.close();
        }

        TimeUtils.waitFor(server::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
    }

    @Test
    public void dynamic_pipeline_changes() throws Exception {
        TimeUtils.delayMillis(100);

        final TCPServer server = new TCPServer();
        server.setInitialOptions(
            (TCPConnectionOptionsHolder) new TCPConnectionOptionsHolder().setLinger(1)
        );
        server.registerOnReceive((connection, data) -> {
            System.out.println("Server.onReceive('" + new String(data) + "')");
        });

        server.run(5421);

        final TCPClient client = new TCPClient();
        // send => Handler_2 => Handler_1     => Server
        // send => A.        => A. (ch.by_h2) => A. (ch.by_h2) (ch.by_h1)
        //         send      => A. B.         => A. B. (ch.by_h1)
        client.getEventPipeline().addHandlerLast(new EventHandler() {
            public byte[] handleSend(EventInvocationContext context, byte[] data) {
                final String message = new String(data);
                System.out.println("Handler_1.handleSend('" + message + "') + 'changed by 1'");
                return (message + " (ch.by_h1)").getBytes();
            }
        });
        client.getEventPipeline().addHandlerLast(new EventHandler() {
            public byte[] handleSend(EventInvocationContext context, byte[] data) {
                final String message = new String(data);
                System.out.println("Handler_2.handleSend('" + message + "') + 'changed by 2'");
                context.getEventPipeline().removeHandler(0);
                context.fireSend(message + " B.");
                return (message + " (ch.by_h2)").getBytes();
            }
        });

        client.connect("localhost", 5421);
        client.send("A.");
        client.awaitWriteDrain(3000);
        client.close();

        TimeUtils.waitFor(client::isClosed, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });

        server.close();
    }

}
