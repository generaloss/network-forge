package generaloss.networkforge;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPConnection;
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

    private static void closeOnOtherSideTest() throws Exception {
        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> System.out.println("[Server] Connected"))
            .setOnDisconnect((connection, message) -> System.out.println("[Server] Disconnected: " + message))
            .run(65000);

        final TCPClient client = new TCPClient()
            .setOnConnect((connection) -> System.out.println("[Client] Connected"))
            .setOnDisconnect((connection, message) -> System.out.println("[Client] Disconnected: " + message));
        for(int i = 0; i < 3; i++){
            client.connect("localhost", 65000);
            client.disconnect();
        }
        TimeUtils.delayMillis(2000);
        server.close();
    }

    @Test
    public void reconnect_client() throws Exception {
        final int reconnectsNum = 100;
        final AtomicInteger counter = new AtomicInteger();
        final TCPServer server = new TCPServer()
                .setOnConnect((connection) -> counter.incrementAndGet())
                .setOnDisconnect((connection, message) -> counter.incrementAndGet())
                .run(65000);

        final TCPClient client = new TCPClient();
        for(int i = 0; i < reconnectsNum; i++){
            client.connect("localhost", 65000);
            client.disconnect();
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
                .setOnDisconnect((connection, message) -> closed.set(true))
                .connect("localhost", 5406);

        TimeUtils.waitFor(closed::get, 5000, Assert::fail);
        server.close();
    }

    @Test
    public void close_server_connection() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean();
        final TCPServer server = new TCPServer()
                .setOnDisconnect((connection, message) -> closed.set(true))
                .run(5407);
        final TCPClient client = new TCPClient();
        client.connect("localhost", 5407);
        client.disconnect();
        TimeUtils.waitFor(closed::get, 500, Assert::fail);
        server.close();
    }

    @Test
    public void send_hello_world_to_server() throws Exception {
        final String message = "Hello, World!";

        final TCPServer server = new TCPServer()
                .setOnReceive((sender, bytes) -> {
                    final String received = new String(bytes);
                    Assert.assertEquals(message, received);
                    sender.close();
                })
                .run(5400);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5400);
        client.send(message.getBytes());

        TimeUtils.waitFor(client::isClosed, 1000, Assert::fail);
        server.close();
    }

    @Test
    public void send_hello_world_encrypted() throws Exception {
        final SecretKey key = generateSecretKey(128);
        final Cipher encryptCipher = getEncryptCipher(key);
        final Cipher decryptCipher = getDecryptCipher(key);

        final String message = "Hello, World!";

        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> connection.encrypt(encryptCipher, decryptCipher))
            .setOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                Assert.assertEquals(message, received);
                sender.close();
            })
            .run(5405);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5405);
        client.encrypt(encryptCipher, decryptCipher);
        client.send(message.getBytes());

        TimeUtils.waitFor(client::isClosed);
        server.close();
    }

    @Test
    public void send_a_lot_of_data_encrypted() throws Exception {
        final SecretKey key = generateSecretKey(128);
        final Cipher encryptCipher = getEncryptCipher(key);
        final Cipher decryptCipher = getDecryptCipher(key);

        final String message = "0123456789".repeat(1000);

        final AtomicInteger counter = new AtomicInteger();
        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> connection.encrypt(encryptCipher, decryptCipher))
            .setOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                counter.incrementAndGet();
                if(!message.equals(received)){
                    Assert.fail();
                    sender.close();
                }
            })
            .run(5408, 5409);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5408 + (int) Math.round(Math.random()));
        client.encrypt(encryptCipher, decryptCipher);

        final int iterations = 10000;
        for(int i = 0; i < iterations; i++)
            client.send(message.getBytes());

        TimeUtils.waitFor(() -> counter.get() == iterations, 5000, Assert::fail);
        server.close();
    }

    @Test
    public void send_hello_world_to_client() throws Exception {
        final String message = "Hello, World!";

        final TCPServer server = new TCPServer()
            .setOnConnect((connection) -> connection.send(message.getBytes()))
            .run(5401);

        final TCPClient client = new TCPClient();
        client.setOnReceive((connection, bytes) -> {
            final String received = new String(bytes);
            Assert.assertEquals(message, received);
            connection.close();
        });
        client.connect("localhost", 5401);

        TimeUtils.waitFor(client::isClosed);
        server.close();
    }

    @Test
    public void send_a_lot_of_data_to_server() throws Exception {
        final String message = "Hello, Data! ".repeat(10000000);

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                Assert.assertEquals(received, message);
                sender.close();
            })
            .run(5402);

        final TCPClient client = new TCPClient();
        client.connect("localhost", 5402);
        client.send(message.getBytes());

        TimeUtils.waitFor(client::isClosed, 2000, Assert::fail);
        server.close();
    }

    @Test
    public void connect_a_lot_of_clients_and_send_a_lot_of_data_multithreaded() throws Exception {
        final String message = "Hello, World! ".repeat(10000);
        final int clientsAmount = 100;
        final AtomicInteger done = new AtomicInteger();

        final TCPServer server = new TCPServer()
            .setOnReceive((sender, bytes) -> {
                final String received = new String(bytes);
                Assert.assertEquals(received, message);
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
                client.disconnect();
            }).start();

        int prevDone = -1;
        while(done.get() != clientsAmount){
            if(done.get() != prevDone)
                prevDone = done.get();
            Thread.onSpinWait();
        }
        server.close();
    }


    @Test
    public void send_packet() throws Exception {
        final String message = "Hello, World!";

        final NetPacketDispatcher dispatcher = new NetPacketDispatcher()
            .register(MsgPacket.class);

        final AtomicInteger counter = new AtomicInteger();
        final MsgHandler handler = (received) -> {
            Assert.assertEquals(message, received);
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

        TimeUtils.waitFor(() -> (counter.get() == 2), 2000, Assert::fail);
        server.close();
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
