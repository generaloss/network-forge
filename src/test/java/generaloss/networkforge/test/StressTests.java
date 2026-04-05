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
import generaloss.networkforge.test.packet.TestEmptyPacket;
import generaloss.networkforge.test.packet.TestMessagePacket;
import generaloss.resourceflow.resource.Resource;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StressTests {

    @Test
    public void reconnect_client_async() throws Exception {
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> counter.incrementAndGet());
        server.registerOnDisconnect((connection, reason) -> counter.incrementAndGet());
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        for(int i = 0; i < reconnectsNum; i++){
            client.connectAsync("localhost", port);
            TimeUtils.waitFor(client::isConnected, 3000, () -> {
                server.close();
                Assert.fail();
            });
            client.close();
        }

        TimeUtils.waitFor(() -> counter.get() == reconnectsNum * 2, 500, () -> Assert.fail(counter.get() + " / " + (reconnectsNum * 2)));
        server.close();
    }

    @Test
    public void reconnect_client_async_2() throws Exception {
        final var reconnectsNum = 100;
        final var counter = new AtomicInteger();

        final var server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> {
            counter.incrementAndGet();
        });
        server.registerOnDisconnect((connection, reason) -> counter.incrementAndGet());
        server.run();
        final int port = server.getPorts()[0];

        final var disconnected = new AtomicBoolean();

        final var client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.registerOnDisconnect((connection, reason) -> {
            disconnected.set(true);
        });

        for(int i = 0; i < reconnectsNum; i++) {
            final var future = client.connectAsync("localhost", port);
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
    public void reconnect_client_sync() throws Exception {
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> counter.incrementAndGet());
        server.registerOnDisconnect((connection, reason) -> counter.incrementAndGet());
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        for(int i = 0; i < reconnectsNum; i++){
            client.connect("localhost", port);
            client.close();
        }

        TimeUtils.waitFor(() -> counter.get() == reconnectsNum * 2, 500, () -> Assert.fail(counter.get() + " / " + (reconnectsNum * 2)));
        server.close();
    }

    @Test
    public void client_on_disconnect() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect(TCPConnection::close);
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.registerOnDisconnect((connection, reason) -> closed.set(true));
        client.connect("localhost", port);

        TimeUtils.waitFor(closed::get, 3000, () -> {
            server.close();
            Assert.fail();
        });
        server.close();
    }

    @Test
    public void server_on_disconnect() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnDisconnect((connection, reason) -> closed.set(true));
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connectAsync("localhost", port);
        TimeUtils.waitFor(client::isConnected, 3000, () -> {
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
        final String message = "0123456789".repeat(1000);

        final AtomicInteger counter = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.getEventPipeline().addHandlerFirst(new DeflateHandler());
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            counter.incrementAndGet();

            if(!message.equals(received)){
                hasNotEqual.set(true);
                sender.close();
            }
        });
        server.run();
        final int port = server.getPorts()[0];

        final int iterations = 10000;

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.getEventPipeline().addHandlerFirst(new DeflateHandler());
        client.registerOnConnect(connection -> {
            for(int i = 0; i < iterations; i++)
                client.send(message);
        });
        client.connect("localhost", port);

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
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> connection.send(message));
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.registerOnReceive((connection, bytes) -> {
            result.set(new String(bytes));
            connection.close();
        });
        client.connect("localhost", port);

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_a_lot_of_data_to_server() throws Exception {
        final String message = "Hello, Data! ".repeat(1000000);
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder();
        options.setMaxFrameSize(message.length());

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.setInitialOptions(options);
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.setInitialOptions(options);
        client.connect("localhost", port);

        client.send(message);

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void ignore_too_large_packets() throws Exception {
        final String message = "Hello, Message! ";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.registerOnConnect(connection ->
            connection.getOptions()
                .setCloseOnFrameReadSizeExceed(false)
                .setMaxFrameSize(message.length())
         );
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connect("localhost", port);

        client.send(message.repeat(2)); // reach bytes limit => will be ignored
        client.send(message);

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void connect_a_lot_of_clients_and_send_a_lot_of_data_multithreaded() throws Exception {
        final String message = "Hello, World! ".repeat(10000);
        final int clientsAmount = 100;
        final AtomicInteger done = new AtomicInteger();
        final AtomicBoolean hasNotEqual = new AtomicBoolean();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            if(!received.equals(message))
                hasNotEqual.set(true);
            done.incrementAndGet();
        });
        server.run();
        final int port = server.getPorts()[0];

        final ConcurrentLinkedQueue<TCPClient> clients = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < clientsAmount; i++){
            final TCPClient client = new TCPClient();
            client.registerOnError(ErrorListener::printError);
            client.connect("localhost", port);
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
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();

        final PacketReader packetReader = new PacketReader();
        final PacketDispatcher packetDispatcher = new PacketDispatcher();

        packetReader.register(TestMessagePacket.class);
        packetDispatcher.register(TestMessagePacket.class, (connection, packet) -> {
            result.set(message);
            counter.incrementAndGet();
        });

        final Executor executor = Executors.newSingleThreadExecutor();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, data) -> {
            packetReader.tryRead(data).ifPresent(
                (packet) -> packetDispatcher.dispatch(sender, packet)
            );
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connect("localhost", port);

        client.send(new TestMessagePacket(message));
        client.send(new TestMessagePacket(message));

        TimeUtils.waitFor(() -> (counter.get() == 2), 3000, () -> { // fail
            client.close();
            server.close();
            Assert.fail("counter=" + counter.get());
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_multiple_packets() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        final PacketReader packetReader = new PacketReader();
        final PacketDispatcher packetDispatcher = new PacketDispatcher();

        packetReader.registerAllFromPackage(Resource.classpath("generaloss/networkforge/test/packet/"));

        packetDispatcher.register(TestMessagePacket.class, (connection, packet) -> counter.incrementAndGet());
        packetDispatcher.register(TestDisconnectPacket.class, (connection, packet) -> counter.incrementAndGet());
        packetDispatcher.register(TestEmptyPacket.class, (connection, packet) -> counter.incrementAndGet());

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, data) -> {
            packetReader.tryRead(data).ifPresent(
                (packet) -> packetDispatcher.dispatch(sender, packet)
            );
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connect("localhost", port);

        client.send(new TestMessagePacket("Hello, World!").createStreamWriter());
        client.send(new TestDisconnectPacket("Disconnection"));
        client.send(new TestEmptyPacket());

        TimeUtils.waitFor(() -> (counter.get() == 3), 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
    }

    @Test
    public void send_packet_ssl() throws Exception {
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();

        final PacketReader packetReader = new PacketReader();
        final PacketDispatcher packetDispatcher = new PacketDispatcher();

        packetReader.register(TestMessagePacket.class, TestMessagePacket::new);
        packetDispatcher.register(TestMessagePacket.class, (connection, packet) -> {
            result.set(message);
            counter.incrementAndGet();
        });

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.getEventPipeline().addHandlerFirst(new ServerSecureHandler());
        server.registerOnReceive((sender, data) -> {
            packetReader.tryRead(data).ifPresent(
                (packet) -> packetDispatcher.dispatch(sender, packet)
            );
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.getEventPipeline().addHandlerFirst(new ClientSecureHandler());
        client.connect("localhost", port);
        client.registerOnConnect(connection -> {
            client.send(new TestMessagePacket(message));
            client.send(new TestMessagePacket(message));
        });

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
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connect("localhost", port);
        client.send(message);

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher));
        server.registerOnReceive((sender, bytes) -> {
            result.set(new String(bytes));
            sender.close();
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.registerOnConnect((connection) -> {
            connection.getCiphers().setCiphers(encryptCipher, decryptCipher);
            client.send(message);
        });
        client.connect("localhost", port);

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
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

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher));
        server.registerOnReceive((sender, bytes) -> {
            final String received = new String(bytes);
            counter.incrementAndGet();

            if(!message.equals(received)){
                hasNotEqual.set(true);
                sender.close();
            }
        });
        server.run(0, 0);
        final int[] ports = server.getPorts();

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.registerOnConnect((connection) -> connection.getCiphers().setCiphers(encryptCipher, decryptCipher));
        client.connect("localhost", ports[(int) Math.round(Math.random())]);

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
    public void async_connect_timeout() {
        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);

        final long timeoutMs = 1000L;

        final CompletableFuture<TCPConnection> future = client.connectAsync("google.com", 65000, timeoutMs);

        try {
            future.join();
            Assert.fail("Expected timeout");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof TimeoutException);
        }
    }

    @Test
    public void send_zero_length_payload_framed() throws Exception {
        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.setCodecFactory(CodecType.FRAMED);
        server.registerOnReceive((c, data) -> {
            if(data.length == 0)
                server.close();
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.setCodec(CodecType.FRAMED);
        client.connect("localhost", port);
        client.send(new byte[0]);

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
    }

    @Test
    public void one_client_multithreaded_send_storm() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final byte[] testData = new byte[] { 54 };
        final int sends = 512;

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.setCodecFactory(CodecType.FRAMED);
        server.registerOnReceive((c, data) -> {
            if(data.length == 1 && data[0] == testData[0])
                counter.incrementAndGet();
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.setCodec(CodecType.FRAMED);
        client.connect("localhost", port);

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
        final AtomicInteger counter = new AtomicInteger();
        final byte[] testData = new byte[] { 54 };
        final int sends = 512;

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.setCodecFactory(CodecType.FRAMED);
        server.registerOnReceive((c, data) -> {
            if(data.length == 1 && data[0] == testData[0])
                counter.incrementAndGet();
        });
        server.run();
        final int port = server.getPorts()[0];

        final Thread[] threads = new Thread[sends];
        for(int i = 0; i < threads.length; i++) {
            final Thread thread = new Thread(() -> {
                try {
                    final TCPClient client = new TCPClient();
                    client.registerOnError(ErrorListener::printError);
                    client.connect("localhost", port);
                    client.send(testData);
                    client.close();
                } catch (Exception e){
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
        final int iterations = 50;
        final AtomicInteger counter = new AtomicInteger();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnDisconnect((connection, reason) -> {
            if(reason == CloseReason.CLOSE_BY_OTHER_SIDE) {
                final int count = counter.incrementAndGet();
                if(count == iterations)
                    server.close();
            } else {
                System.err.println(reason);
            }
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        for(int i = 0; i < iterations; i++) {
            client.connect("localhost", port);
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
        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((connection, data) ->
            System.out.println("Server.onReceive('" + new String(data) + "')")
        );

        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder();
        options.setLinger(1);
        server.setInitialOptions(options);

        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        // send => Handler_2 => Handler_1     => Server
        // send => A.        => A. (ch.by_h2) => A. (ch.by_h2) (ch.by_h1)
        //         send      => A. B.         => A. B. (ch.by_h1)
        client.getEventPipeline().addHandlerLast(new EventHandler() {
            public boolean handleSend(EventInvocationContext context, byte[] data) {
                final String message = new String(data);
                System.out.println("Handler_1.handleSend('" + message + "') + 'changed by 1'");
                context.send((message + " (ch.by_h1)").getBytes());
                return false;
            }
        });
        client.getEventPipeline().addHandlerLast(new EventHandler() {
            public boolean handleSend(EventInvocationContext context, byte[] data) {
                final String message = new String(data);
                System.out.println("Handler_2.handleSend('" + message + "') + 'changed by 2'");
                context.getEventPipeline().removeHandler(0);
                context.send((message + " (ch.by_h2)").getBytes());
                context.send(message + " B.");
                return false;
            }
        });

        client.connect("localhost", port);
        client.send("A.");
        client.awaitWriteDrain(3000);
        client.close();

        TimeUtils.waitFor(client::isNotConnected, 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });

        server.close();
    }

    @Test
    public void packet_batching() throws Exception {
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();
        final int sendCount = 500;
        final AtomicInteger maxBatched = new AtomicInteger();

        final PacketReader packetReader = new PacketReader();
        final PacketDispatcher packetDispatcher = new PacketDispatcher();

        packetReader.register(TestMessagePacket.class);
        packetDispatcher.register(TestMessagePacket.class, (connection, packet) -> {
            result.set(message);
            counter.incrementAndGet();
        });

        final List<NetPacket> packetsBatchList = new ArrayList<>();

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, data) -> {
            packetReader.tryRead(data)
                .ifPresent(packetsBatchList::add);
        });
        server.registerOnReadComplete((connection) -> {
            if(packetsBatchList.isEmpty())
                return;

            packetDispatcher.dispatch(connection, packetsBatchList);
            final int batched = packetsBatchList.size();
            maxBatched.set(Math.max(maxBatched.get(), batched));
            packetsBatchList.clear();
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connect("localhost", port);

        for(int i = 0; i < sendCount; i++)
            client.send(new TestMessagePacket(message));

        TimeUtils.waitFor(() -> (counter.get() == sendCount), 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void packet_async_handling() throws Exception {
        final String message = "Hello, World!";
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();
        final int sendCount = 10;
        final AtomicInteger maxBatched = new AtomicInteger();

        final PacketReader packetReader = new PacketReader();
        final PacketDispatcher packetDispatcher = new PacketDispatcher()
            .async(Executors.newSingleThreadExecutor());

        packetReader.register(TestMessagePacket.class);
        packetDispatcher.register(TestMessagePacket.class, (connection, packet) -> {
            result.set(message);
            counter.incrementAndGet();
        });

        final TCPServer server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnReceive((sender, data) -> {
            final NetPacket packet = packetReader.readOrNull(data);
            if(packet != null)
                packetDispatcher.dispatch(sender, packet);
        });
        server.run();
        final int port = server.getPorts()[0];

        final TCPClient client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.connect("localhost", port);

        for(int i = 0; i < sendCount; i++)
            client.send(new TestMessagePacket(message));

        TimeUtils.waitFor(() -> (counter.get() == sendCount), 3000, () -> {
            client.close();
            server.close();
            Assert.fail();
        });
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void async_connect() throws Exception {
        final AtomicBoolean server_on_connected = new AtomicBoolean();
        final AtomicBoolean server_on_receive = new AtomicBoolean();
        final AtomicBoolean server_on_disconnected = new AtomicBoolean();
        final AtomicBoolean client_on_connected = new AtomicBoolean();
        final AtomicBoolean client_on_disconnected = new AtomicBoolean();

        // server
        final var server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect((connection) -> {
            server_on_connected.set(true);
        });
        server.registerOnReceive((sender, data) -> {
            server_on_receive.set(true);
        });
        server.registerOnDisconnect((connection, reason) -> {
            server_on_disconnected.set(true);
        });
        server.run();
        final int port = server.getPorts()[0];

        // client
        final var client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.registerOnConnect((connection) -> {
            client_on_connected.set(true);
        });
        client.registerOnDisconnect((connection, reason) -> {
            client_on_disconnected.set(true);
        });
        final var future = client.connectAsync("localhost", port);

        // waiters
        TimeUtils.waitFor(future::isDone, 3000, () -> {
            server.close();
            Assert.fail(future.toString());
        });
        TimeUtils.waitFor(client_on_connected::get, 3000, () -> {
            server.close();
            Assert.fail();
        });
        TimeUtils.waitFor(server_on_connected::get, 3000, () -> {
            server.close();
            Assert.fail();
        });

        client.send("data");

        TimeUtils.waitFor(server_on_receive::get, 3000, () -> {
            server.close();
            Assert.fail();
        });

        client.close();

        TimeUtils.waitFor(client_on_disconnected::get, 3000, () -> {
            server.close();
            Assert.fail();
        });
        TimeUtils.waitFor(server_on_disconnected::get, 3000, () -> {
            server.close();
            Assert.fail();
        });

        server.close();
    }

    @Test
    public void fastest_connect_success() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean();

        final var server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnConnect(conn -> connected.set(true));
        server.run();
        final int port = server.getPorts()[0];

        final var client = new TCPClient();

        final SocketAddress[] addresses = new SocketAddress[] {
            new InetSocketAddress("localhost", 1111), // wrong address 1
            new InetSocketAddress("localhost", 1112), // wrong address 2
            new InetSocketAddress("localhost", 1113), // wrong address 3
            new InetSocketAddress("localhost", 1114), // wrong address 4
            new InetSocketAddress("localhost", 1115), // wrong address 5
            new InetSocketAddress("localhost", 1116), // wrong address 6
            new InetSocketAddress("localhost", 1117), // wrong address 7
            new InetSocketAddress("localhost", 1118), // wrong address 8
            new InetSocketAddress("localhost", port),
            new InetSocketAddress("localhost", 1119), // wrong address 9
        };

        final var future = client.connectFastest(addresses, 3000);

        TimeUtils.waitFor(future::isDone, 10000, () -> {
            Assert.fail(future.toString());
        });

        Assert.assertTrue(future.isDone());
        if(future.isCompletedExceptionally())
            Assert.fail(future.toString());

        TimeUtils.waitFor(connected::get, 3000, Assert::fail);

        client.close();
        server.close();
    }

    @Test
    public void fastest_connect_fail() {
        final var client = new TCPClient();
        client.registerOnError(ErrorListener::printError);

        final SocketAddress[] addresses = new SocketAddress[] {
            new InetSocketAddress("localhost", 1111), // wrong address 1
            new InetSocketAddress("localhost", 1112), // wrong address 2
            new InetSocketAddress("localhost", 1113), // wrong address 3
            new InetSocketAddress("localhost", 1114), // wrong address 4
            new InetSocketAddress("localhost", 1115), // wrong address 5
            new InetSocketAddress("localhost", 1116), // wrong address 6
            new InetSocketAddress("localhost", 1117), // wrong address 7
            new InetSocketAddress("localhost", 1118), // wrong address 8
            new InetSocketAddress("localhost", 1119), // wrong address 9
        };

        final var future = client.connectFastest(addresses, 1000);

        TimeUtils.waitFor(future::isDone, 3000, Assert::fail);

        Assert.assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void poorly_synchronized_client() throws Exception {
        final AtomicBoolean disconnected = new AtomicBoolean();
        final AtomicBoolean received = new AtomicBoolean();

        final var server = new TCPServer();
        server.registerOnError(ErrorListener::printError);
        server.registerOnDisconnect((connection, reason) -> {
            disconnected.set(true);
            System.out.println("Server disconnect " + reason);
        });
        server.registerOnReceive((connection ,data) -> received.set(true));
        server.run();
        final int port = server.getPorts()[0];

        final var client = new TCPClient();
        client.registerOnError(ErrorListener::printError);
        client.setCodec(CodecType.STREAM);
        client.connect("localhost", port);

        final int size = -1000;
        client.send(new byte[] {
            (byte) ((size >>> 24) & 0xFF),
            (byte) ((size >>> 16) & 0xFF),
            (byte) ((size >>> 8) & 0xFF),
            (byte) (size & 0xFF),
        }); // header
        client.send(new byte[] { 0, 0 }); // frame (2 bytes)

        client.awaitWriteDrain(3000);

        TimeUtils.waitFor(disconnected::get, 3000, Assert::fail);
        Assert.assertFalse(received.get());
    }

}
