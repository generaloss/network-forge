package generaloss.networkforge;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPConnectionType;
import generaloss.networkforge.tcp.TCPServer;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class StressTest {

    public static void main(String[] args) {
        reliabilityTest();
        // closeOnOtherSideTest();
        // webpageConnect();
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
    }

    private static void webpageConnect() throws Exception {
        final AtomicReference<String> result = new AtomicReference<>();

        final TCPClient client = new TCPClient()
            .setConnectionType(TCPConnectionType.STREAM)
            .setOnReceive((connection, bytes) -> {
                result.set(new String(bytes));
                connection.close();
            })
            .connect("google.com", 80);

        client.send("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n");

        TimeUtils.waitFor(client::isClosed, 5000);
        Assert.assertTrue(result.get().startsWith("HTTP/1.1"));
    }

}
