package generaloss.networkforge.test;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPServer;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;

import java.io.IOException;

public class VulnerabilitiesCheck {

    public static void main(String[] args) {
        // memoryOverflow(); // 25.12.1 passed
    }

    private static void memoryOverflow() throws IOException {
        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder();
        options.setCloseOnFrameReadSizeExceed(false);

        final TCPServer server = new TCPServer();
        server.setInitialOptions(options);
        server.setCodecFactory(CodecType.FRAMED);
        server.run(5400);

        final TCPClient client = new TCPClient();
        client.setInitialOptions(options);
        client.setCodec(CodecType.STREAM);
        client.connect("localhost", 5400);

        // send maximum frame size
        final byte[] maxSizeFrameHeader = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}; // int
        client.send(maxSizeFrameHeader);

        // trying to overflow the memory on the server
        final byte[] message = "DATA".repeat(10_000).getBytes();
        while(client.isOpen()) {
            client.send(message);
            TimeUtils.delayMillis(1);
        }

        TimeUtils.waitFor(server::isClosed, 60_000);
    }

}
