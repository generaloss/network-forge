package generaloss.networkforge;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.TCPConnectionType;
import generaloss.resourceflow.stream.BinaryInputStream;
import generaloss.resourceflow.stream.BinaryOutputStream;
import generaloss.networkforge.packet.NetPacket;
import generaloss.networkforge.packet.NetPacketDispatcher;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TcpTests {

    private static SecretKey generateSecretKey(int size) {
        try{
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(size);
            return generator.generateKey();
        }catch(NoSuchAlgorithmException ignored){
            return null;
        }
    }

    private static Cipher getEncryptCipher(SecretKey key) {
        try{
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher;

        }catch(NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            throw new RuntimeException(e);
        }
    }

    private static Cipher getDecryptCipher(SecretKey key) {
        try{
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher;

        }catch(NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        reliabilityTest();
        // closeOnOtherSideTest();
    }

    private static void reliabilityTest() {
        // iterate all tests 1000 times
        final TcpTests tests = new TcpTests();
        for(int i = 0; i < 1000; i++){
            System.out.println(i + 1);
            for(Method method: TcpTests.class.getMethods()){
                if(method.isAnnotationPresent(Test.class)){
                    method.setAccessible(true);
                    try {
                        method.invoke(tests);
                    }catch(Exception e) {
                        throw new RuntimeException("Exception in test '" + method.getName() + "': ", e);
                    }
                }
            }
        }
    }

    private static void closeOnOtherSideTest() {
        try {
            final TCPServer server = new TCPServer()
                .setOnConnect(
                    (connection) -> System.out.println("[Server] Connected")
                )
                .setOnDisconnect((connection, reason, e) -> System.out.println("[Server] Disconnected: " + reason))
                .run(65000);

            final TCPClient client = new TCPClient().setOnConnect(
                    (connection) -> System.out.println("[Client] Connected"))
                .setOnDisconnect((connection, reason, e) -> System.out.println("[Client] Disconnected: " + reason));
            for(int i = 0; i < 3; i++) {
                client.connect("localhost", 65000);
                client.close();
                TimeUtils.delayMillis(500);
            }
            TimeUtils.delayMillis(2000);
            server.close();
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void webpage_connect() throws Exception {
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPClient client = new TCPClient()
            .setConnectionType(TCPConnectionType.STREAM)
            .setOnReceive((connection, bytes) -> {
                result.set(new String(bytes));
                connection.close();
            })
            .connect("google.com", 80);

        client.send("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n");

        TimeUtils.waitFor(client::isClosed, 2000);
        Assert.assertTrue(result.get().startsWith("HTTP/1.1"));
    }

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
        final SecretKey key = generateSecretKey(128);
        final Cipher encryptCipher = getEncryptCipher(key);
        final Cipher decryptCipher = getDecryptCipher(key);

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
        final SecretKey key = generateSecretKey(128);
        final Cipher encryptCipher = getEncryptCipher(key);
        final Cipher decryptCipher = getDecryptCipher(key);

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

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5408 + (int) Math.round(Math.random()));
        client.setCiphers(encryptCipher, decryptCipher);

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

        final TCPClient client = new TCPClient();
        client.setOnReceive((connection, bytes) -> {
            result.set(new String(bytes));
            connection.close();
        });
        client.connect("localhost", 5401);

        TimeUtils.waitFor(client::isClosed);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    @Test
    public void send_a_lot_of_data_to_server() throws Exception {
        final String message = "Hello, Data! ".repeat(1000000);
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                result.set(new String(bytes));
                sender.close();
            })
            .run(5402);

        final TCPClient client = new TCPClient()
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

        final List<TCPClient> clients = new CopyOnWriteArrayList<>();
        for(int i = 0; i < clientsAmount; i++){
            final TCPClient client = new TCPClient();
            client.connect("localhost", 5404);
            clients.add(client);
        }

        for(TCPClient client: clients)
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

        final NetPacketDispatcher dispatcher = new NetPacketDispatcher()
            .register(MsgPacket.class);

        final AtomicInteger counter = new AtomicInteger();
        final MsgHandler handler = (received) -> {
            result.set(received);
            counter.incrementAndGet();
        };

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                dispatcher.readPacket(bytes, handler);
                dispatcher.handlePackets();
            })
            .run(5403);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5403);
        client.send(new MsgPacket(message));
        client.send(new MsgPacket(message));

        TimeUtils.waitFor(() -> (counter.get() == 2), 2000);
        server.close();
        Assert.assertEquals(message, result.get());
    }

    interface MsgHandler {
        void handleMsg(String message);
    }

    static class MsgPacket extends NetPacket<MsgHandler> {
        private String message;
        public MsgPacket(String message) {
            this.message = message;
        }
        public MsgPacket() { }
        public void write(BinaryOutputStream stream) throws IOException {
            stream.writeByteString(message);
        }
        public void read(BinaryInputStream stream) throws IOException {
            message = stream.readByteString();
        }
        public void handle(MsgHandler handler) {
            handler.handleMsg(message);
        }
    }

}
