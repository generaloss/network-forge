package generaloss.networkforge.test;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class VulnerabilitiesCheck {

    public static void main(String[] args) throws Exception {
        final TCPServer server = new TCPServer()
                                     .setCodecFactory(CodecType.FRAMED)
                                     .registerOnReceive((c, b) -> System.out.println("Received: " + b.length))
                                     .run(5400);

        final TCPClient client = new TCPClient()
                                     .setCodec(CodecType.FRAMED)
                                     .connect("localhost", 5400);

        client.send(new byte[0]);

        TimeUtils.waitFor(server::isClosed, 60_000);


        // memoryOverflow(); // 25.12.1 passed
    }

    private static void memoryOverflow() throws IOException, TimeoutException {
        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder()
                .setCloseOnFrameReadSizeExceed(false);

        final TCPServer server = new TCPServer()
                .setInitialOptions(options)
                .setCodecFactory(CodecType.FRAMED)
                .run(5400);

        final TCPClient client = new TCPClient()
                .setInitialOptions(options)
                .setCodec(CodecType.STREAM)
                .connect("localhost", 5400);

        // send maximum frame size
        final byte[] maxSizeFrameHeader = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}; // int
        client.send(maxSizeFrameHeader);

        // trying to overflow the memory on the server
        final byte[] message = "DATA".repeat(10_000).getBytes();
        while(client.isConnected()) {
            client.send(message);
            TimeUtils.delayMillis(1);
        }

        TimeUtils.waitFor(server::isClosed, 60_000);
    }

}
